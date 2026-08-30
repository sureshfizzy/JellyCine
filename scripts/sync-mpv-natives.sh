#!/usr/bin/env bash
# Sync GLES libmpv natives into phone/src/main/jniLibs/<abi>.
#
# Do NOT compile ffmpeg/mpv inside this Windows Gradle tree.
# mpv-android/buildscripts only work on Linux/macOS (WSL is unsupported).
# mpv-android 2026-08-11 ships FFmpeg 9.0 (LIBAVUTIL_61) and libmpv @ f4d13e1.
#
# Always copy the whole .so set, never libmpv.so alone.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_ROOT="$ROOT/phone/src/main/jniLibs"
SOURCE_FILE="$DEST_ROOT/SOURCE.txt"
DEFAULT_TAG="2026-08-11"
EXPECTED_SONAME="LIBAVUTIL_61"
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
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

from-release  GitHub mpv-android tag, asset app-default-universal-release.apk
from-apk      local mpv-android / Yamby APK or split
from-yamby    adb pull com.hush.yamby split_config.arm64_v8a.apk
from-prefix   copy after a Linux mpv-android buildscripts run:
                PREFIX_LIB_DIR = buildscripts/prefix/arm64/lib
                PLAYER_SO      = app/build/.../libplayer.so

Default tag $DEFAULT_TAG is libmpv (mpv @ f4d13e1) / FFmpeg 9.0 ($EXPECTED_SONAME).
EOF
}

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing $1" >&2
    exit 1
  }
}

python_bin() {
  if command -v python3 >/dev/null 2>&1; then
    echo python3
  elif command -v python >/dev/null 2>&1; then
    echo python
  else
    echo "missing python3" >&2
    exit 1
  fi
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
      echo "missing $name in $dir" >&2
      missing=1
    fi
  done
  [ "$missing" -eq 0 ] || exit 1

  local soname
  soname="$(soname_of "$dir/libmpv.so")"
  echo "libmpv NEEDED FFmpeg: ${soname:-unknown} ($dir)"
  if [ -z "$soname" ]; then
    echo "could not read LIBAVUTIL_* from libmpv.so" >&2
    exit 1
  fi
  if [ "$soname" != "$EXPECTED_SONAME" ] && [ "$ALLOW_ABI_BUMP" -eq 0 ]; then
    echo "refusing $soname (want $EXPECTED_SONAME). pass --allow-abi-bump after JNI/FFmpeg review." >&2
    exit 1
  fi
  if strings "$dir/libmpv.so" 2>/dev/null | grep -qi 'libvulkan'; then
    echo "warning: libmpv.so references Vulkan; force gpu-api=opengl at runtime" >&2
  fi
}

install_set() {
  local src="$1"
  local abi="$2"
  local dest="$DEST_ROOT/$abi"
  verify_set "$src"
  mkdir -p "$dest"
  find "$dest" -maxdepth 1 -type f -name '*.so' -delete
  local name
  for name in "${REQUIRED[@]}"; do
    cp -f "$src/$name" "$dest/$name"
  done
  find "$src" -maxdepth 1 -type f -name '*.so' -print0 |
    while IFS= read -r -d '' extra; do
      name="$(basename "$extra")"
      [ -e "$dest/$name" ] || cp -f "$extra" "$dest/$name"
    done
  echo "installed $abi into $dest"
}

write_source() {
  local note="$1"
  mkdir -p "$DEST_ROOT"
  cat >"$SOURCE_FILE" <<EOF
source=$note
abis=${ABIS[*]}
expected_ffmpeg_soname=$EXPECTED_SONAME
ffmpeg=9.0
mpv-android_tag=$TAG
note=Do not mix with org.jellycine.mpv AAR. Update via scripts/sync-mpv-natives.sh
synced_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
  rm -f "$DEST_ROOT/arm64-v8a/SOURCE.txt"
}

extract_apk() {
  local apk="$1"
  local out="$2"
  "$(python_bin)" - "$apk" "$out" "${ABIS[@]}" <<'PY'
import sys, zipfile, pathlib
apk = sys.argv[1]
out = pathlib.Path(sys.argv[2])
abis = sys.argv[3:]
out.mkdir(parents=True, exist_ok=True)
copied = {abi: 0 for abi in abis}
with zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        name = info.filename.replace("\\", "/")
        for abi in abis:
            prefix = f"lib/{abi}/"
            if name.startswith(prefix) and name.endswith(".so"):
                dest_dir = out / abi
                dest_dir.mkdir(parents=True, exist_ok=True)
                dest = dest_dir / name.rsplit("/", 1)[-1]
                dest.write_bytes(z.read(info))
                copied[abi] += 1
if not any(copied.values()):
    raise SystemExit(f"no lib/<abi>/*.so in {apk}")
for abi, count in copied.items():
    if count:
        print(f"extracted {count} so files for {abi}")
PY
}

from_release() {
  need curl
  python_bin >/dev/null
  local api="https://api.github.com/repos/mpv-android/mpv-android/releases/tags/${TAG}"
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  local apk="$tmp/mpv-android.apk"
  local url
  url="$("$(python_bin)" - "$api" <<'PY'
import json, sys, urllib.request
api = sys.argv[1]
with urllib.request.urlopen(api) as resp:
    data = json.load(resp)
want = "app-default-universal-release.apk"
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
  local abi
  for abi in "${ABIS[@]}"; do
    [ -d "$tmp/libs/$abi" ] || {
      echo "apk missing $abi" >&2
      exit 1
    }
    install_set "$tmp/libs/$abi" "$abi"
  done
  write_source "mpv-android tag=$TAG apk=app-default-universal-release.apk"
}

from_apk() {
  local apk="$1"
  [ -f "$apk" ] || { echo "apk not found: $apk" >&2; exit 1; }
  python_bin >/dev/null
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  extract_apk "$apk" "$tmp/libs"
  local installed=0
  local abi
  for abi in "${ABIS[@]}"; do
    if [ -d "$tmp/libs/$abi" ]; then
      install_set "$tmp/libs/$abi" "$abi"
      installed=1
    fi
  done
  [ "$installed" -eq 1 ] || {
    echo "no matching ABI libs in $apk" >&2
    exit 1
  }
  write_source "apk=$apk"
}

from_yamby() {
  need adb
  python_bin >/dev/null
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
  install_set "$tmp/libs/arm64-v8a" "arm64-v8a"
  write_source "yamby=com.hush.yamby split_config.arm64_v8a.apk"
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
  install_set "$tmp" "arm64-v8a"
  write_source "prefix=$libdir player=$player"
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
    elif [ -f "$DEST_ROOT/arm64-v8a/SOURCE.txt" ]; then
      echo
      echo "current $DEST_ROOT/arm64-v8a/SOURCE.txt:"
      cat "$DEST_ROOT/arm64-v8a/SOURCE.txt"
    fi
    ;;
  *)
    echo "unknown command: $cmd" >&2
    usage
    exit 1
    ;;
esac
