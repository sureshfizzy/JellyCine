#!/usr/bin/env bash
# Sync GLES libmpv natives into phone/src/main/jniLibs/arm64-v8a.
#
# Do NOT compile ffmpeg/mpv inside this Windows Gradle tree.
# mpv-android/buildscripts only work on Linux/macOS (WSL is unsupported).
# Latest mpv-android (2026-08-11) ships FFmpeg 9 — different SONAME than
# the current Yamby/mpv 0.41 + LIBAVUTIL_60 set that matches colour.
#
# Default release tag is the last mpv-android build that still lists
# libmpv 0.41.0. Always copy the whole .so set, never libmpv.so alone.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/phone/src/main/jniLibs/arm64-v8a"
SOURCE_FILE="$DEST/SOURCE.txt"
DEFAULT_TAG="2025-12-27"
EXPECTED_SONAME="LIBAVUTIL_60"
ABI="arm64-v8a"
REQUIRED=(
  libmpv.so
  libplayer.so
  libavcodec.so
  libavdevice.so
  libavfilter.so
  libavformat.so
  libavutil.so
  libswresample.so
  libswscale.so
  libc++_shared.so
  libass.so
)

ALLOW_ABI_BUMP=0
TAG="$DEFAULT_TAG"

usage() {
  cat <<EOF
Usage:
  $0 from-release [--tag $DEFAULT_TAG] [--allow-abi-bump]
  $0 from-apk PATH.apk [--allow-abi-bump]
  $0 from-yamby [--allow-abi-bump]
  $0 from-prefix PREFIX_LIB_DIR PLAYER_SO [--allow-abi-bump]

from-release  GitHub mpv-android tag, asset app-default-arm64-v8a-release.apk
from-apk      local mpv-android / Yamby arm64 APK or split
from-yamby    adb pull com.hush.yamby split_config.arm64_v8a.apk
from-prefix   copy after a Linux mpv-android buildscripts run:
                PREFIX_LIB_DIR = buildscripts/prefix/arm64/lib
                PLAYER_SO      = app/build/.../libplayer.so

Default tag $DEFAULT_TAG is libmpv 0.41.0 / FFmpeg 8 (LIBAVUTIL_60).
Do not pass 2026-08-11 unless you also bump Kotlin JNI and accept FFmpeg 9.
EOF
}

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing $1" >&2
    exit 1
  }
}

parse_allow() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --allow-abi-bump) ALLOW_ABI_BUMP=1 ;;
      --tag)
        shift
        TAG="${1:-}"
        [ -n "$TAG" ] || { echo "--tag needs a value" >&2; exit 1; }
        ;;
      --tag=*) TAG="${1#--tag=}" ;;
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
}

soname_of() {
  local so="$1"
  if command -v llvm-readelf >/dev/null 2>&1; then
    llvm-readelf -V "$so" 2>/dev/null | tr ' ' '\n' | grep -E '^LIBAVUTIL_[0-9]+$' | head -n 1 || true
  elif command -v readelf >/dev/null 2>&1; then
    readelf -V "$so" 2>/dev/null | tr ' ' '\n' | grep -E '^LIBAVUTIL_[0-9]+$' | head -n 1 || true
  else
    strings "$so" 2>/dev/null | grep -E '^LIBAVUTIL_[0-9]+$' | head -n 1 || true
  fi
}

verify_set() {
  local dir="$1"
  local missing=0
  local name
  for name in "${REQUIRED[@]}"; do
    if [ ! -s "$dir/$name" ]; then
      echo "missing $name" >&2
      missing=1
    fi
  done
  [ "$missing" -eq 0 ] || exit 1

  local soname
  soname="$(soname_of "$dir/libmpv.so")"
  echo "libmpv NEEDED FFmpeg: ${soname:-unknown}"
  if [ -z "$soname" ]; then
    echo "could not read LIBAVUTIL_* from libmpv.so" >&2
    exit 1
  fi
  if [ "$soname" != "$EXPECTED_SONAME" ] && [ "$ALLOW_ABI_BUMP" -eq 0 ]; then
    echo "refusing $soname (want $EXPECTED_SONAME). pass --allow-abi-bump after JNI/FFmpeg review." >&2
    exit 1
  fi
  if strings "$dir/libmpv.so" 2>/dev/null | grep -qi 'libvulkan'; then
    echo "warning: libmpv.so references Vulkan; Hills-style Vulkan/libplacebo washed skin orange" >&2
  fi
}

install_set() {
  local src="$1"
  local note="$2"
  verify_set "$src"
  mkdir -p "$DEST"
  local name
  for name in "${REQUIRED[@]}"; do
    cp -f "$src/$name" "$DEST/$name"
  done
  # Extra SONAMEs (dav1d/curl/mbedtls/...) if the new libmpv dynamically needs them.
  find "$src" -maxdepth 1 -type f -name '*.so' -print0 |
    while IFS= read -r -d '' extra; do
      name="$(basename "$extra")"
      [ -e "$DEST/$name" ] || cp -f "$extra" "$DEST/$name"
    done
  cat >"$SOURCE_FILE" <<EOF
source=$note
abi=$ABI
expected_ffmpeg_soname=$EXPECTED_SONAME
synced_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
  echo "installed into $DEST"
}

extract_apk() {
  local apk="$1"
  local out="$2"
  python - "$apk" "$out" "$ABI" <<'PY'
import sys, zipfile, pathlib
apk, out, abi = sys.argv[1], pathlib.Path(sys.argv[2]), sys.argv[3]
prefix = f"lib/{abi}/"
out.mkdir(parents=True, exist_ok=True)
copied = 0
with zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        name = info.filename.replace("\\", "/")
        if not name.startswith(prefix) or not name.endswith(".so"):
            continue
        dest = out / name.rsplit("/", 1)[-1]
        dest.write_bytes(z.read(info))
        copied += 1
if copied == 0:
    raise SystemExit(f"no {prefix}*.so in {apk}")
print(f"extracted {copied} so files from {apk}")
PY
}

from_release() {
  need python
  need curl
  local api="https://api.github.com/repos/mpv-android/mpv-android/releases/tags/${TAG}"
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  local apk="$tmp/mpv-android.apk"
  local url
  url="$(python - "$api" <<'PY'
import json, sys, urllib.request
api = sys.argv[1]
with urllib.request.urlopen(api) as resp:
    data = json.load(resp)
want = "app-default-arm64-v8a-release.apk"
for asset in data.get("assets", []):
    if asset.get("name") == want:
        print(asset["browser_download_url"])
        break
else:
    raise SystemExit(f"{want} not in {api}")
PY
)"
  echo "downloading $url"
  curl -fL --retry 3 -o "$apk" "$url"
  extract_apk "$apk" "$tmp/libs"
  install_set "$tmp/libs" "mpv-android tag=$TAG apk=app-default-arm64-v8a-release.apk"
}

from_apk() {
  local apk="$1"
  [ -f "$apk" ] || { echo "apk not found: $apk" >&2; exit 1; }
  need python
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  extract_apk "$apk" "$tmp/libs"
  install_set "$tmp/libs" "apk=$apk"
}

from_yamby() {
  need adb
  need python
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  local path
  path="$(adb shell pm path com.hush.yamby | tr -d '\r' | grep 'split_config.arm64_v8a.apk' | sed 's/^package://')"
  [ -n "$path" ] || {
    echo "com.hush.yamby arm64 split not installed" >&2
    exit 1
  }
  adb pull "$path" "$tmp/yamby.apk"
  extract_apk "$tmp/yamby.apk" "$tmp/libs"
  install_set "$tmp/libs" "yamby=com.hush.yamby split_config.arm64_v8a.apk"
}

from_prefix() {
  local libdir="$1"
  local player="$2"
  [ -d "$libdir" ] || { echo "prefix lib dir missing: $libdir" >&2; exit 1; }
  [ -f "$player" ] || { echo "libplayer.so missing: $player" >&2; exit 1; }
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  cp -f "$libdir"/*.so "$tmp/" 2>/dev/null || true
  cp -f "$player" "$tmp/libplayer.so"
  install_set "$tmp" "prefix=$libdir player=$player"
}

cmd="${1:-}"
shift || true
case "$cmd" in
  from-release)
    parse_allow "$@"
    from_release
    ;;
  from-apk)
    apk="${1:-}"
    [ -n "$apk" ] || { usage; exit 1; }
    shift
    parse_allow "$@"
    from_apk "$apk"
    ;;
  from-yamby)
    parse_allow "$@"
    from_yamby
    ;;
  from-prefix)
    libdir="${1:-}"
    player="${2:-}"
    [ -n "$libdir" ] && [ -n "$player" ] || { usage; exit 1; }
    shift 2
    parse_allow "$@"
    from_prefix "$libdir" "$player"
    ;;
  ""|-h|--help)
    usage
    if [ -f "$SOURCE_FILE" ]; then
      echo
      echo "current $SOURCE_FILE:"
      cat "$SOURCE_FILE"
    fi
    ;;
  *)
    echo "unknown command: $cmd" >&2
    usage
    exit 1
    ;;
esac
