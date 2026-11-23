#!/usr/bin/env bash
# Reproducible ARM64 Build & Signing Workflow for Bare Browser + Native Gaming Mode
# Requirements (per BareBrowser/bare-browser official docs):
#   - x86-64 Linux host (or Docker Desktop with >= 32 GB RAM and >= 120 GB free disk)
#   - git, docker, keytool, zipalign, apksigner (Android Build-Tools)
#
# Usage:
#   ./build-bare-gaming-arm64.sh [BARE_REPO_DIR] [KEYSTORE_PATH] [KEY_ALIAS]

set -euo pipefail

BARE_REPO_DIR="${1:-$PWD/bare-browser}"
KEYSTORE_PATH="${2:-$PWD/bare-gaming-release.jks}"
KEY_ALIAS="${3:-baregaming}"
OVERLAY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="$PWD/bare-gaming-arm64-build.log"

echo "=== [1/7] Clone or update official BareBrowser/bare-browser ==="
if [ ! -d "$BARE_REPO_DIR/.git" ]; then
  git clone https://github.com/BareBrowser/bare-browser.git "$BARE_REPO_DIR"
else
  git -C "$BARE_REPO_DIR" fetch --all --tags
fi

cd "$BARE_REPO_DIR"

echo "=== [2/7] Inspect pinned Chromium commit from Bare documentation ==="
echo "Check BUILDING.md / docs/build-from-source.md for the exact pinned Chromium tag/commit."
echo "Do NOT use tip-of-tree Chromium; patches must match Bare's pinned revision."

echo "=== [3/7] Build and start Bare Browser Ubuntu 22.04 Docker container ==="
./builder.sh build
./builder.sh start

echo "=== [4/7] Stage Bare patches, uBlock Origin CRX, version GN args, and Gaming Mode sources ==="
mkdir -p exchange/gaming_overlay
cp -R patches exchange/
cp third_party/ublock_origin/uBlockOrigin-*.crx exchange/
tools/version.py gn arm64 > exchange/version-args.gn
cp -R "$OVERLAY_DIR/src" exchange/gaming_overlay/
cp "$OVERLAY_DIR/gn/gaming_arm64_overlay.gn" exchange/gaming_overlay/

echo "=== [5/7] Run Chromium checkout, Bare patch application, and ARM64 Ninja build inside container ==="
echo "NOTE: Set CHROMIUM_PINNED_REV to the exact commit/tag listed in Bare's BUILDING.md before executing."
: "${CHROMIUM_PINNED_REV:?Set CHROMIUM_PINNED_REV from BareBrowser/bare-browser BUILDING.md first}"

./builder.sh shell <<EOF 2>&1 | tee "$LOG_FILE"
set -euo pipefail

if [ ! -d "\$HOME/depot_tools" ]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "\$HOME/depot_tools"
fi
export PATH="\$HOME/depot_tools:\$PATH"

mkdir -p /work/chromium
cd /work/chromium

if [ ! -d "/work/chromium/src/.git" ]; then
  fetch --nohooks android
fi

cd /work/chromium/src
git fetch origin --tags
git reset --hard
git clean -fd
git checkout "${CHROMIUM_PINNED_REV}"
gclient sync -D --force --reset
./build/install-build-deps.sh --android --no-prompt

# Apply official Bare Browser patch series first
git apply --check /exchange/patches/*.patch
git apply --index /exchange/patches/*.patch

# Stage bundled uBlock Origin CRX required by Bare
mkdir -p chrome/browser/resources/bare
cp /exchange/uBlockOrigin-*.crx chrome/browser/resources/bare/ublock_origin.crx

# Copy native Gaming Mode Java & C++ files into Chromium source tree
cp -v /exchange/gaming_overlay/src/chrome/android/java/src/org/chromium/chrome/browser/gaming/BareGamingModeController.java \
  chrome/android/java/src/org/chromium/chrome/browser/gaming/BareGamingModeController.java || true
mkdir -p chrome/android/java/src/org/chromium/chrome/browser/gaming chrome/browser/gaming
cp -v /exchange/gaming_overlay/src/chrome/android/java/src/org/chromium/chrome/browser/gaming/BareGamingModeController.java \
  chrome/android/java/src/org/chromium/chrome/browser/gaming/
cp -v /exchange/gaming_overlay/src/chrome/browser/gaming/bare_gaming_mode_bridge.h \
  chrome/browser/gaming/
cp -v /exchange/gaming_overlay/src/chrome/browser/gaming/bare_gaming_mode_bridge.cc \
  chrome/browser/gaming/

# Assemble GN arguments (Bare version args + ARM64 Gaming overlay)
mkdir -p out/Default
cat /exchange/version-args.gn /exchange/gaming_overlay/gn/gaming_arm64_overlay.gn > out/Default/args.gn
gn gen out/Default

# Compile native Chromium ARM64 APK
autoninja -C out/Default chrome_public_apk

cp out/Default/apks/ChromePublic.apk /exchange/BareGaming-arm64-unsigned.apk
EOF

echo "=== [6/7] Align and sign ARM64 APK ==="
UNSIGNED_APK="$BARE_REPO_DIR/exchange/BareGaming-arm64-unsigned.apk"
ALIGNED_APK="$BARE_REPO_DIR/exchange/BareGaming-arm64-aligned.apk"
SIGNED_APK="$BARE_REPO_DIR/exchange/BareGaming-arm64-signed.apk"

if [ ! -f "$KEYSTORE_PATH" ]; then
  echo "Generating local release keystore at $KEYSTORE_PATH (replace with your own for production)..."
  keytool -genkeypair -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10000
fi

zipalign -f -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
apksigner sign --ks "$KEYSTORE_PATH" --ks-key-alias "$KEY_ALIAS" \
  --out "$SIGNED_APK" "$ALIGNED_APK"
apksigner verify --verbose "$SIGNED_APK"

echo "=== [7/7] Build complete ==="
echo "Signed ARM64 APK: $SIGNED_APK"
echo "Compilation log:  $LOG_FILE"
