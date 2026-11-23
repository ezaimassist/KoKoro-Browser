# On-Device Verification Protocol: Bare Browser + Gaming Mode on LastHavens

Because this AI Studio container has no Chromium build toolchain or Android device/ADB access, execute this verification matrix on a physical ARM64 Android phone (e.g., Snapdragon 8 Gen 2/3 or Dimensity 9xxx, >= 8 GB RAM) after installing `BareGaming-arm64-signed.apk`.

## 1. Install & Verify Native Chromium Architecture
```bash
adb install -r BareGaming-arm64-signed.apk
adb shell dumpsys package <bare_package_name> | grep -E "versionName|primaryCpuAbi"
```
Confirm `primaryCpuAbi=arm64-v8a`.
Open `bare://gpu` inside the installed browser and record:
- `GL_RENDERER` (e.g., `ANGLE (Qualcomm, Vulkan 1.3.x, Adreno ...)`)
- `WebGL2`: `Hardware accelerated`
- `Vulkan`: `Enabled` (or GLES fallback if driver blocklisted)
- `Skia Renderer`: `Graphite` or `Ganesh Vulkan`

Open `bare://process-internals` and verify separate **Browser**, **GPU**, and **Renderer** processes are active.

## 2. Live ADB Diagnostics During LastHavens Test
In a separate terminal before launching LastHavens:
```bash
adb logcat -c
adb logcat -v time | grep -iE "chromium|v8|wasm|webgl|ANGLE|Vulkan|lmkd|lowmemorykiller|OutOfMemory" > lasthavens-logcat.txt
```

## 3. LastHavens Functional & Stress Test Matrix
Test `https://lasthavens.com` with **Gaming Mode OFF** (baseline) and **Gaming Mode ON**:

| Stage | Pass Criteria | Key Failure Signals to Watch |
|---|---|---|
| **1. Bootstrap & Login** | ~50 MB initial Wasm/asset bundle streams cleanly via `WebAssembly.instantiateStreaming`; login prompt accepts credentials without crash. | `CompileError`, `TypeError: Response has unsupported MIME type`, `SharedArrayBuffer is not defined` (COOP/COEP stripped by extension). |
| **2. Character Selection** | 3D WebGL2 character model renders with no context loss; UI responds immediately to touch. | `CONTEXT_LOST_WEBGL`, blank canvas, >500ms shader compile stall. |
| **3. World Loading & Entry** | Zone terrain/MPQ assets stream and cache without stalling; world renders smoothly with no frozen frames for >= 5 minutes of movement/combat. | Renderer OOM / `lmkd` kill in logcat, `QuotaExceededError`, >200ms frame freezes during asset decompression. |
| **4. Refresh Rate vs. Game FPS** | Verify Gaming Mode HUD reports **Display Hz** (e.g. 120 Hz) separately from **Actual Game Swap FPS** (`DidCommitAndDrawCompositorFrame` / Viz swap rate). | Never treat Display Hz as Game FPS; validate with `adb shell dumpsys gfxinfo <pkg> framestats`. |

## 4. Frame Pacing & Input Latency Capture (Perfetto / gfxinfo)
```bash
adb shell dumpsys gfxinfo <bare_package_name> reset
# Play LastHavens for 60 seconds (camera pan + combat + zone movement)
adb shell dumpsys gfxinfo <bare_package_name> framestats > lasthavens-framestats.txt
```
Inspect `Janky frames`, `50th / 90th / 95th / 99th percentile` frame times, and compare **Gaming Mode OFF** vs **Gaming Mode ON** on the exact same in-game scene.
