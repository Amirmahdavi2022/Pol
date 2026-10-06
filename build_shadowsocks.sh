#!/bin/bash

# We can't use rust-android-gradle to build shadowsocks-rust, since it uses functionality that
# breaks with gradle 9. Rust binaries for shadowsocks can be built via shadowsocks-android which
# uses the plugin...
# Rust Android Gradle Plugin:
# https://github.com/mozilla/rust-android-gradle/
# Open PR for gradle 9 support:
# https://github.com/mozilla/rust-android-gradle/pull/168

set -eo pipefail

if ! command -v rustup >/dev/null 2>&1; then
  echo >&2 "Error: rustup is not installed. Please install before running this script."
  exit 1
fi

cd "$(dirname "$0")"
ROOT="$(pwd -P)"
BUILDDIR="$(mktemp -d)"
LOG="$ROOT/build_shadowsocks.log"

echo "Build log: $LOG"

cd "$BUILDDIR"

echo "Builddir: $BUILDDIR" > "$LOG" 2>&1

echo "- Cloning shadowsocks-android and submodules…"
git clone --recursive --shallow-submodules --depth 1 https://github.com/shadowsocks/shadowsocks-android >> "$LOG" 2>&1

echo "- Adding android native targets for rust…"
rustup target add armv7-linux-androideabi aarch64-linux-android x86_64-linux-android >> "$LOG" 2>&1

echo "- Building shadowsocks-android…"
cd "shadowsocks-android"
./gradlew mergeReleaseJniLibFolders -PTARGET_ABI=arm >> "$LOG" 2>&1
./gradlew mergeReleaseJniLibFolders -PTARGET_ABI=arm64 >> "$LOG" 2>&1
./gradlew mergeReleaseJniLibFolders -PTARGET_ABI=x86_64 >> "$LOG" 2>&1

echo "- Copy created so files…"
cp -a core/build/rustJniLibs/android/* "$ROOT/app/src/main/jniLibs/" >> "$LOG" 2>&1
echo "- Cleanup…"
cd "$ROOT"
rm -rf "$BUILDDIR"
