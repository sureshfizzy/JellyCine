#!/usr/bin/env bash
# Build a signed release APK and install it with adb.
#
# Defaults to the phone module, the connected device ABI, replace-install,
# and launching the app. Matches the local test loop:
#   ./gradlew :phone:assembleRelease
#   adb install -r phone/build/outputs/apk/release/vela-phone-release-*-arm64-v8a.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="phone"
SERIAL="${ANDROID_SERIAL:-}"
ABI=""
SKIP_BUILD=0
LAUNCH=1
REINSTALL=0
GRADLE_ARGS=()

usage() {
  cat <<EOF
Usage:
  $0 [options] [-- gradle-args...]

Build a release APK, pick the split that matches the device ABI, then
adb install -r. Launch the app afterwards.

Options:
  --phone           phone module (default)
  --tv              tv module
  --serial SERIAL   adb device serial (or set ANDROID_SERIAL)
  --abi ABI         force APK ABI (arm64-v8a, armeabi-v7a, x86_64, x86)
  --skip-build      install the already-built APK
  --no-launch       install only, do not start the app
  --reinstall       uninstall first (clears app data)
  -h, --help        show this help

Examples:
  $0
  $0 --tv
  $0 --serial ea60f9c7
  $0 --skip-build --no-launch
EOF
}

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing $1" >&2
    exit 1
  }
}

resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    if [ -n "$sdk_root" ] && [ -x "$sdk_root/platform-tools/adb" ]; then
      echo "$sdk_root/platform-tools/adb"
      return
    fi
  done
  echo "adb not found; install platform-tools or add it to PATH" >&2
  exit 1
}

version_name() {
  sed -n 's/.*appVersionName *= *"\([^"]*\)".*/\1/p' "$ROOT/build.gradle" | head -n 1
}

connected_serials() {
  "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

pick_serial() {
  if [ -n "$SERIAL" ]; then
    echo "$SERIAL"
    return
  fi

  local devices
  devices="$(connected_serials)"
  if [ -z "$devices" ]; then
    echo "no adb device in 'device' state" >&2
    "$ADB" devices >&2
    exit 1
  fi

  local count
  count="$(printf '%s\n' "$devices" | grep -c .)"
  if [ "$count" -gt 1 ]; then
    echo "multiple devices; pass --serial or set ANDROID_SERIAL" >&2
    printf '%s\n' "$devices" >&2
    exit 1
  fi
  printf '%s\n' "$devices"
}

device_abi() {
  local serial="$1"
  local abi
  abi="$("$ADB" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
  case "$abi" in
    arm64-v8a|armeabi-v7a|x86_64|x86) echo "$abi" ;;
    *)
      echo "unsupported device ABI: ${abi:-empty}" >&2
      exit 1
      ;;
  esac
}

apk_path() {
  local module="$1"
  local version="$2"
  local abi="$3"
  echo "$ROOT/$module/build/outputs/apk/release/vela-${module}-release-${version}-${abi}.apk"
}

package_name() {
  case "$1" in
    phone) echo "com.vela.app" ;;
    tv) echo "com.vela.tv" ;;
    *)
      echo "unknown module: $1" >&2
      exit 1
      ;;
  esac
}

launch_component() {
  echo "$(package_name "$1")/.ui.activity.VelaActivity"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --phone) MODULE="phone" ;;
    --tv) MODULE="tv" ;;
    --serial)
      shift
      SERIAL="${1:-}"
      [ -n "$SERIAL" ] || { echo "--serial needs a value" >&2; exit 1; }
      ;;
    --serial=*) SERIAL="${1#--serial=}" ;;
    --abi)
      shift
      ABI="${1:-}"
      [ -n "$ABI" ] || { echo "--abi needs a value" >&2; exit 1; }
      ;;
    --abi=*) ABI="${1#--abi=}" ;;
    --skip-build) SKIP_BUILD=1 ;;
    --no-launch) LAUNCH=0 ;;
    --reinstall) REINSTALL=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      GRADLE_ARGS+=("$@")
      break
      ;;
    *)
      echo "unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

need java
ADB="$(resolve_adb)"
VERSION="$(version_name)"
[ -n "$VERSION" ] || {
  echo "could not read appVersionName from build.gradle" >&2
  exit 1
}

SERIAL="$(pick_serial)"
if [ -z "$ABI" ]; then
  ABI="$(device_abi "$SERIAL")"
fi
APK="$(apk_path "$MODULE" "$VERSION" "$ABI")"
PACKAGE="$(package_name "$MODULE")"

echo "module  $MODULE"
echo "version $VERSION"
echo "device  $SERIAL"
echo "abi     $ABI"
echo "apk     $APK"

if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "building :$MODULE:assembleRelease"
  (
    cd "$ROOT"
    if [ "${#GRADLE_ARGS[@]}" -gt 0 ]; then
      ./gradlew ":$MODULE:assembleRelease" "${GRADLE_ARGS[@]}"
    else
      ./gradlew ":$MODULE:assembleRelease"
    fi
  )
fi

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  echo "available release outputs:" >&2
  ls -lh "$ROOT/$MODULE/build/outputs/apk/release" 2>/dev/null >&2 || true
  exit 1
fi

if [ "$REINSTALL" -eq 1 ]; then
  echo "uninstalling $PACKAGE"
  "$ADB" -s "$SERIAL" uninstall "$PACKAGE" >/dev/null 2>&1 || true
fi

echo "installing"
"$ADB" -s "$SERIAL" install -r "$APK"

if [ "$LAUNCH" -eq 1 ]; then
  local_component="$(launch_component "$MODULE")"
  echo "launching $local_component"
  "$ADB" -s "$SERIAL" shell am start -n "$local_component"
fi

echo "done"
