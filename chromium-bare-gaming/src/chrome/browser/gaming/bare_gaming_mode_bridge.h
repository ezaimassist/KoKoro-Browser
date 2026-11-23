// Copyright 2026 Bare Browser Gaming Extension Contributors
// Target path inside Bare Browser Chromium checkout:
// chrome/browser/gaming/bare_gaming_mode_bridge.h

#ifndef CHROME_BROWSER_GAMING_BARE_GAMING_MODE_BRIDGE_H_
#define CHROME_BROWSER_GAMING_BARE_GAMING_MODE_BRIDGE_H_

#include <jni.h>
#include <string>

#include "base/android/scoped_java_ref.h"
#include "base/memory/weak_ptr.h"
#include "base/time/time.h"
#include "base/timer/timer.h"
#include "content/public/browser/web_contents_observer.h"

namespace content {
class WebContents;
}

namespace bare_gaming {

// Browser-process bridge connecting BareGamingModeController.java to Chromium's
// multiprocess architecture:
//  - Elevates foreground WebGL/Wasm RenderProcessHost binding importance
//  - Freezes inactive background tabs (PageLifecycleState) to prevent LMK kills
//  - Samples real GPU process backend info from GpuDataManagerImpl
//  - Tracks actual compositor/WebGL frame presentation cadence (distinct from Display Hz)
class BareGamingModeBridge : public content::WebContentsObserver {
 public:
  BareGamingModeBridge(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj);
  BareGamingModeBridge(const BareGamingModeBridge&) = delete;
  BareGamingModeBridge& operator=(const BareGamingModeBridge&) = delete;
  ~BareGamingModeBridge() override;

  void Destroy(JNIEnv* env);

  void SetGamingModeForActiveContents(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& j_active_web_contents,
      jboolean enabled,
      jint render_scale_percent,
      jboolean bypass_extension_filter);

  void SetBackgroundTabFrozen(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& j_background_web_contents,
      jboolean frozen);

  // content::WebContentsObserver:
  void DidCommitAndDrawCompositorFrame() override;

 private:
  void PublishTelemetryTick();
  std::string QueryActiveGpuBackendSummary() const;

  base::android::ScopedJavaGlobalRef<jobject> java_ref_;
  bool gaming_mode_enabled_ = false;
  int render_scale_percent_ = 100;
  bool bypass_extension_filter_ = false;

  // Actual committed frame counter (measures real rendered game/compositor swaps,
  // NOT Android's Display.getRefreshRate() VSync cadence).
  uint32_t committed_frames_in_window_ = 0;
  base::TimeTicks window_start_time_;
  base::RepeatingTimer telemetry_timer_;

  base::WeakPtrFactory<BareGamingModeBridge> weak_factory_{this};
};

}  // namespace bare_gaming

#endif  // CHROME_BROWSER_GAMING_BARE_GAMING_MODE_BRIDGE_H_
