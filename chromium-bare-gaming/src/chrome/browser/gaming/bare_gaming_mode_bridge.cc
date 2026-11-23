// Copyright 2026 Bare Browser Gaming Extension Contributors
// Target path inside Bare Browser Chromium checkout:
// chrome/browser/gaming/bare_gaming_mode_bridge.cc

#include "chrome/browser/gaming/bare_gaming_mode_bridge.h"

#include <algorithm>

#include "base/android/jni_string.h"
#include "base/functional/bind.h"
#include "base/strings/stringprintf.h"
#include "chrome/android/chrome_jni_headers/BareGamingModeController_jni.h"
#include "content/public/browser/android/child_process_importance.h"
#include "content/public/browser/gpu_data_manager.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/browser/render_process_host.h"
#include "content/public/browser/web_contents.h"
#include "gpu/config/gpu_info.h"

namespace bare_gaming {

BareGamingModeBridge::BareGamingModeBridge(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& obj)
    : java_ref_(env, obj), window_start_time_(base::TimeTicks::Now()) {}

BareGamingModeBridge::~BareGamingModeBridge() = default;

void BareGamingModeBridge::Destroy(JNIEnv* env) {
  telemetry_timer_.Stop();
  Observe(nullptr);
  delete this;
}

void BareGamingModeBridge::SetGamingModeForActiveContents(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& j_active_web_contents,
    jboolean enabled,
    jint render_scale_percent,
    jboolean bypass_extension_filter) {
  gaming_mode_enabled_ = static_cast<bool>(enabled);
  render_scale_percent_ = std::clamp(static_cast<int>(render_scale_percent), 70, 100);
  bypass_extension_filter_ = static_cast<bool>(bypass_extension_filter);

  content::WebContents* web_contents =
      content::WebContents::FromJavaWebContents(j_active_web_contents);
  if (!web_contents) {
    telemetry_timer_.Stop();
    Observe(nullptr);
    return;
  }

  Observe(web_contents);

  // Pin active game renderer process importance to protect large WebAssembly
  // heaps (e.g. LastHavens 500MB-1.5GB linear memory + WebGL2 buffers) from
  // Android's lowmemorykiller (lmkd) during zone transitions.
  if (gaming_mode_enabled_) {
    web_contents->SetPrimaryMainFrameImportance(
        content::ChildProcessImportance::IMPORTANT);
  } else {
    web_contents->SetPrimaryMainFrameImportance(
        content::ChildProcessImportance::NORMAL);
  }

  // Note: When wiring into Blink's WebGLRenderingContextBase & RendererScheduler,
  // forward (gaming_mode_enabled_, render_scale_percent_) via a Mojo interface
  // bound on web_contents->GetPrimaryMainFrame()->GetRemoteInterfaces().
  // And when bypass_extension_filter_ is true, register the primary origin in
  // Bare's ExtensionWebRequestEventRouter fast-path allowlist so large streamed
  // .wasm / game asset chunks bypass per-request JS filter overhead while
  // keeping uBlock Origin active on all other origins.

  committed_frames_in_window_ = 0;
  window_start_time_ = base::TimeTicks::Now();

  if (gaming_mode_enabled_) {
    telemetry_timer_.Start(
        FROM_HERE, base::Milliseconds(500),
        base::BindRepeating(&BareGamingModeBridge::PublishTelemetryTick,
                            weak_factory_.GetWeakPtr()));
  } else {
    telemetry_timer_.Stop();
  }
}

void BareGamingModeBridge::SetBackgroundTabFrozen(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& j_background_web_contents,
    jboolean frozen) {
  content::WebContents* wc =
      content::WebContents::FromJavaWebContents(j_background_web_contents);
  if (!wc || wc == web_contents()) {
    return;
  }

  // Only freeze hidden background tabs so active gameplay keeps full CPU/RAM headroom.
  if (frozen) {
    if (wc->GetVisibility() == content::Visibility::HIDDEN &&
        !wc->IsCurrentlyAudible()) {
      wc->SetPageFrozen(true);
    }
  } else {
    wc->SetPageFrozen(false);
  }
}

void BareGamingModeBridge::DidCommitAndDrawCompositorFrame() {
  if (gaming_mode_enabled_) {
    ++committed_frames_in_window_;
  }
}

void BareGamingModeBridge::PublishTelemetryTick() {
  const base::TimeTicks now = base::TimeTicks::Now();
  const double elapsed_sec = (now - window_start_time_).InSecondsF();
  float actual_fps = 0.0f;
  if (elapsed_sec > 0.05) {
    actual_fps = static_cast<float>(committed_frames_in_window_ / elapsed_sec);
  }
  committed_frames_in_window_ = 0;
  window_start_time_ = now;

  JNIEnv* env = base::android::AttachCurrentThread();
  std::string gpu_summary = QueryActiveGpuBackendSummary();
  Java_BareGamingModeController_onNativeTelemetryUpdate(
      env, java_ref_, actual_fps,
      base::android::ConvertUTF8ToJavaString(env, gpu_summary));
}

std::string BareGamingModeBridge::QueryActiveGpuBackendSummary() const {
  const gpu::GPUInfo info = content::GpuDataManager::GetInstance()->GetGPUInfo();
  const std::string renderer =
      info.gl_renderer.empty() ? "Initializing GPU Process" : info.gl_renderer;
  const std::string backend = info.gl_version.empty() ? "GLES/Vulkan" : info.gl_version;
  return base::StringPrintf("%s | %s", renderer.c_str(), backend.c_str());
}

static jlong JNI_BareGamingModeController_Init(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& jcaller) {
  return reinterpret_cast<intptr_t>(new BareGamingModeBridge(env, jcaller));
}

}  // namespace bare_gaming
