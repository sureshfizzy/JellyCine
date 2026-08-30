#!/usr/bin/env bash
# Extract the user-facing GitHub Release body for a version.
# Source of truth: docs/release-notes.md (## x.y.z sections).
# This is not a commit changelog generator.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NOTES_FILE="${NOTES_FILE:-$ROOT/docs/release-notes.md}"
VERSION=""
OUT=""
CHECK_ONLY=0
SELF_TEST=0
FOOTER="请根据设备架构选择对应 APK。"

usage() {
  cat <<EOF
Usage:
  $0 [--version X.Y.Z] [--out FILE] [--check]
  $0 --self-test

Extract the ## x.y.z section from docs/release-notes.md and format it as
a GitHub Release body (intro + bullets + architecture footer).

Options:
  --version X.Y.Z   version to extract (default: appVersionName in build.gradle)
  --out FILE        write body to FILE (also printed to stdout)
  --check           validate only, do not print the body
  --self-test       run extractor fixtures
  -h, --help        show this help
EOF
}

read_app_version() {
  sed -n 's/.*appVersionName *= *"\([^"]*\)".*/\1/p' "$ROOT/build.gradle" | head -n 1
}

normalize_version() {
  local raw="$1"
  raw="${raw#"${raw%%[![:space:]]*}"}"
  raw="${raw%"${raw##*[![:space:]]}"}"
  raw="${raw#v}"
  raw="${raw#V}"
  printf '%s' "$raw"
}

extract_section() {
  local file="$1"
  local version="$2"
  awk -v ver="$version" '
    function trim(s) {
      gsub(/\r/, "", s)
      gsub(/^[ \t]+|[ \t]+$/, "", s)
      return s
    }
    {
      line = trim($0)
    }
    line ~ /^## / {
      heading = line
      sub(/^##[ \t]+/, "", heading)
      sub(/^[vV]/, "", heading)
      if (found) exit
      if (heading == ver) {
        found = 1
        next
      }
    }
    found { print $0 }
    END {
      if (!found) exit 2
    }
  ' "$file"
}

trim_body() {
  awk '
    {
      gsub(/\r/, "")
      lines[NR] = $0
    }
    END {
      start = 1
      end = NR
      while (start <= end && lines[start] ~ /^[ \t]*$/) start++
      while (end >= start && lines[end] ~ /^[ \t]*$/) end--
      for (i = start; i <= end; i++) print lines[i]
    }
  '
}

looks_like_changelog() {
  grep -qiE 'Full Changelog|What.?s Changed|^## Changelog' <<<"$1"
}

has_bullet() {
  grep -qE '^[[:space:]]*[-*][[:space:]]+[^[:space:]]' <<<"$1"
}

has_intro() {
  awk '
    BEGIN { found = 0 }
    /^[[:space:]]*$/ { next }
    /^[[:space:]]*[-*][[:space:]]+/ { next }
    { found = 1; exit }
    END { exit found ? 0 : 1 }
  ' <<<"$1"
}

ensure_footer() {
  local body="$1"
  local last
  last="$(printf '%s\n' "$body" | awk 'NF { last=$0 } END { print last }')"
  if [ "$last" = "$FOOTER" ]; then
    printf '%s\n' "$body"
    return
  fi
  if [ -n "$body" ]; then
    printf '%s\n\n%s\n' "$body" "$FOOTER"
  else
    printf '%s\n' "$FOOTER"
  fi
}

compose_body() {
  local file="$1"
  local version="$2"
  local raw body

  if [ ! -f "$file" ]; then
    echo "missing release notes file: $file" >&2
    return 1
  fi

  if ! raw="$(extract_section "$file" "$version")"; then
    echo "docs/release-notes.md 缺少 ## ${version} 章节。" >&2
    echo "发版前请写面向用户的更新说明（简介 + 要点列表），不要使用 GitHub 自动 changelog。" >&2
    return 1
  fi

  body="$(printf '%s\n' "$raw" | trim_body)"
  if [ -z "$body" ]; then
    echo "## ${version} 章节为空。" >&2
    return 1
  fi
  if looks_like_changelog "$body"; then
    echo "## ${version} 看起来是 changelog，而不是面向用户的更新说明。" >&2
    return 1
  fi
  if ! has_intro "$body"; then
    echo "## ${version} 需要一句简介，不要只列要点。" >&2
    return 1
  fi
  if ! has_bullet "$body"; then
    echo "## ${version} 需要至少一条以 - 开头的要点。" >&2
    return 1
  fi

  ensure_footer "$body"
}

fail() {
  [ -n "${_TEST_DIR:-}" ] && rm -rf "$_TEST_DIR"
  echo "self-test failed: $*" >&2
  exit 1
}

self_test() {
  local dir notes body real_version
  dir="$(mktemp -d)"
  notes="$dir/release-notes.md"
  _TEST_DIR="$dir"

  cat >"$notes" <<'EOF'
# 更新说明

## 1.0.1

关于页可检查更新。

- 按架构推荐 APK

## 1.0.0

Vela 首个正式版本。

- 提供 Android Phone 与 Android TV 客户端

请根据设备架构选择对应 APK。
EOF

  NOTES_FILE="$notes"
  body="$(compose_body "$notes" "1.0.1")"
  grep -q '关于页可检查更新。' <<<"$body" || fail "1.0.1 intro"
  grep -qF -- '- 按架构推荐 APK' <<<"$body" || fail "1.0.1 bullet"
  grep -q "$FOOTER" <<<"$body" || fail "1.0.1 footer appended"
  if grep -q 'Vela 首个正式版本' <<<"$body"; then
    fail "1.0.1 leaked 1.0.0"
  fi

  body="$(compose_body "$notes" "1.0.0")"
  [ "$(grep -c "$FOOTER" <<<"$body")" -eq 1 ] || fail "1.0.0 footer not duplicated"
  grep -q '提供 Android Phone' <<<"$body" || fail "1.0.0 bullet"

  if compose_body "$notes" "9.9.9" >/dev/null 2>"$dir/missing.err"; then
    fail "missing version should fail"
  fi
  grep -q '缺少 ## 9.9.9' "$dir/missing.err" || fail "missing version message"

  cat >"$notes" <<'EOF'
## 2.0.0

Full Changelog: v1.0.0...v2.0.0
EOF
  if compose_body "$notes" "2.0.0" >/dev/null 2>"$dir/changelog.err"; then
    fail "changelog body should fail"
  fi
  grep -q 'changelog' "$dir/changelog.err" || fail "changelog message"

  cat >"$notes" <<'EOF'
## 2.0.0

只有简介没有要点。
EOF
  if compose_body "$notes" "2.0.0" >/dev/null 2>"$dir/bullet.err"; then
    fail "missing bullets should fail"
  fi
  grep -q '至少一条' "$dir/bullet.err" || fail "missing bullet message"

  cat >"$notes" <<'EOF'
## 2.0.0

- 只有要点没有简介
EOF
  if compose_body "$notes" "2.0.0" >/dev/null 2>"$dir/intro.err"; then
    fail "missing intro should fail"
  fi
  grep -q '简介' "$dir/intro.err" || fail "missing intro message"

  real_version="$(read_app_version)"
  [ -n "$real_version" ] || fail "read appVersionName"
  compose_body "$ROOT/docs/release-notes.md" "$real_version" >/dev/null \
    || fail "committed notes for $real_version"

  rm -rf "$dir"
  _TEST_DIR=""
  echo "self-test ok"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --version)
      shift
      VERSION="${1:-}"
      [ -n "$VERSION" ] || { echo "--version needs a value" >&2; exit 1; }
      ;;
    --version=*) VERSION="${1#--version=}" ;;
    --out)
      shift
      OUT="${1:-}"
      [ -n "$OUT" ] || { echo "--out needs a value" >&2; exit 1; }
      ;;
    --out=*) OUT="${1#--out=}" ;;
    --check) CHECK_ONLY=1 ;;
    --self-test) SELF_TEST=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

if [ "$SELF_TEST" -eq 1 ]; then
  self_test
  exit 0
fi

if [ -z "$VERSION" ]; then
  VERSION="$(read_app_version)"
fi
VERSION="$(normalize_version "$VERSION")"
[ -n "$VERSION" ] || {
  echo "could not determine version" >&2
  exit 1
}

BODY="$(compose_body "$NOTES_FILE" "$VERSION")"
TITLE="Vela v${VERSION}"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "title=$TITLE"
    echo "version=$VERSION"
  } >> "$GITHUB_OUTPUT"
fi

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "ok $TITLE"
  exit 0
fi

if [ -n "$OUT" ]; then
  printf '%s\n' "$BODY" > "$OUT"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "body_path=$OUT" >> "$GITHUB_OUTPUT"
  fi
fi

printf '%s\n' "$BODY"
