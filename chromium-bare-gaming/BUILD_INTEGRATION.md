# Bare Browser Chromium `BUILD.gn` & Lifecycle Wiring Guide

Apply these exact wiring steps inside `/work/chromium/src` after applying Bare Browser's official `patches/*.patch` series and copying the files from `chromium-bare-gaming/src/`.

## 1. JNI & Java Build Wiring (`chrome/android/BUILD.gn`)
1. Add `java/src/org/chromium/chrome/browser/gaming/BareGamingModeController.java` to `chrome_java_sources` (or `chrome_java` target sources).
2. Add `java/src/org/chromium/chrome/browser/gaming/BareGamingModeController.java` to `chrome_jni_headers` (`generate_jni("chrome_jni_headers")`) so `chrome/android/chrome_jni_headers/BareGamingModeController_jni.h` is generated automatically by `jni_zero`.

## 2. Native Browser Process Build Wiring (`chrome/browser/BUILD.gn`)
Inside the `if (is_android)` block of `static_library("browser")`:
```gn
sources += [
  "gaming/bare_gaming_mode_bridge.cc",
  "gaming/bare_gaming_mode_bridge.h",
]
```

## 3. Activity & Compositor Hooks
- **`ChromeActivity.java`** (inside `onStartWithNative` / `onDestroy`):
  - Instantiate `mBareGamingModeController = new BareGamingModeController(this, getTabModelSelector(), getCompositorViewHolderForTesting());` (or `mCompositorViewHolder`).
  - Invoke `mBareGamingModeController.destroy()` in `onDestroy()`.
- **`CompositorViewHolder.java`** (inside `dispatchTouchEvent(MotionEvent e)`):
  - Forward `MotionEvent` to `mBareGamingModeController.onCompositorTouchEvent(e)` before `super.dispatchTouchEvent(e)` so Chromium's touch pipeline bypasses Android VSync input coalescing when Gaming Mode is active.
- **`ExtensionWebRequestEventRouter` (Bare Browser Manifest V2 / uBlock Origin path)**:
  - Add an opt-in per-origin Gaming Mode passthrough check so trusted game origins (such as `lasthavens.com`) do not suffer IO-thread regex/header mutation delays on large streamed `.wasm` / terrain chunks or lose `COOP`/`COEP` headers required for `SharedArrayBuffer`.
