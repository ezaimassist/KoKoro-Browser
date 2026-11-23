// Copyright 2026 Bare Browser Gaming Extension Contributors
// Native Chromium Patch Reference & Implementation Notes for:
//  1) WebGL 2.0 High-Performance Context & Low-Latency Swapchain
//  2) WebAssembly Streaming Compilation & Large Asset Memory Stability (LastHavens)
//  3) GPU Command Buffer & ANGLE/Vulkan Acceleration on Android ARM64
//
// Apply these targeted edits inside the Bare Browser Chromium checkout AFTER
// applying Bare's official `patches/*.patch` series (rebase hunks to Bare's pinned commit).

// ============================================================================
// PATCH 1: WebGL 2.0 High-Performance & Desynchronized Default in Gaming Mode
// File: third_party/blink/renderer/modules/webgl/webgl_rendering_context_base.cc
// ============================================================================
/*
In WebGLRenderingContextBase::Create(...), immediately after normalizing
WebGLContextAttributes `attributes`:

  if (RuntimeEnabledFeatures::BareGamingModeEnabled()) {
    // Prefer discrete / high-performance GPU queue and avoid costly implicit
    // alpha/preserveDrawingBuffer readbacks on mobile tile-based GPUs (Adreno/Mali).
    if (attributes.power_preference ==
        CanvasContextCreationAttributesCore::PowerPreference::kDefault) {
      attributes.power_preference =
          CanvasContextCreationAttributesCore::PowerPreference::kHighPerformance;
    }
    // Enable low-latency desynchronized presentation when Android SurfaceControl
    // is active and the game did not explicitly request preserveDrawingBuffer.
    if (!attributes.preserve_drawing_buffer &&
        RuntimeEnabledFeatures::BareGamingDesynchronizedWebGLEnabled()) {
      attributes.desynchronized = true;
    }
  }
*/

// ============================================================================
// PATCH 2: Large WebAssembly Streaming Compilation & Memory Headroom
// Files:
//   - content/browser/renderer_host/render_process_host_impl.cc
//   - storage/browser/quota/quota_settings.cc
//   - third_party/blink/renderer/bindings/core/v8/v8_wasm_response_extensions.cc
// ============================================================================
/*
Why LastHavens (WoW 3.3.5a WebGL2 + Wasm) hits Wasm stream errors & frozen frames:
  (A) Android 64-bit Chromium V8 Wasm code space & dynamic tiering:
      In RenderProcessHostImpl::PropagateBrowserCommandLineToRenderer(...), when
      Bare Gaming Mode is active on ARM64 devices with >= 6 GB RAM, pass V8 flags:
        --js-flags=--wasm-dynamic-tiering,--liftoff,--wasm-lazy-compilation
      This guarantees Liftoff compiles large Wasm modules rapidly at login/character
      select without main-thread stalls, while TurboFan optimizes hot world-loop
      functions asynchronously on background compiler threads.

  (B) Streamed Asset Cache Eviction & Quota Throttling:
      LastHavens streams ~50 MB bootstrap + hundreds of MB of world/MPQ assets into
      Cache Storage / IndexedDB / HTTP disk cache.
      In storage/browser/quota/quota_settings.cc (CalculateNominalDynamicSettings):
      Ensure the per-origin pool is not aggressively capped when device free storage
      exceeds 4 GB, preventing abrupt QuotaExceededError / truncated Wasm streams
      mid-world-load.

  (C) uBlock Origin / Extension WebRequest Interference with Streaming Wasm:
      Bare Browser bundles full Manifest V2 uBlock Origin. When a game streams
      hundreds of asset chunks or opens binary WebSockets, synchronous extension
      webRequest listeners on the IO thread can introduce latency spikes or alter
      headers required for Cross-Origin Isolation (COOP/COEP -> SharedArrayBuffer)
      and `WebAssembly.instantiateStreaming()` (`Content-Type: application/wasm`).
      In extensions/browser/api/web_request/extension_web_request_event_router.cc:
      Add an opt-in per-origin Gaming Mode passthrough check so user-trusted game
      origins bypass extension body/header mutation while preserving COOP/COEP
      and `application/wasm` streaming integrity.
*/

// ============================================================================
// PATCH 3: GPU Command Buffer Transfer Memory & ANGLE/Vulkan Stability
// Files:
//   - gpu/command_buffer/client/shared_memory_limits.h
//   - gpu/config/gpu_finch_features.cc
// ============================================================================
/*
In gpu/command_buffer/client/shared_memory_limits.h:
  For ARM64 Android devices with >= 6 GB RAM (SysInfo::AmountOfPhysicalMemoryMB() >= 6144),
  increase SharedMemoryLimits::ForWebGLContext() mapped_memory_reclaim_limit and
  max_transfer_buffer_size from conservative mobile defaults (e.g. 4 MB -> 16 MB)
  so large LastHavens world texture/vertex buffer uploads do not repeatedly flush
  the GPU command buffer ring and stall the renderer main thread.

In chrome/browser/about_flags.cc (exposed under bare://flags):
  Register explicit user-controllable flags (avoiding blind forced Vulkan on
  broken OEM drivers where Bare Browser previously hit GPU process init crashes):
    - #bare-gaming-mode            (Master Gaming Mode & process priority pinning)
    - #bare-gaming-angle-vulkan    (Prefer ANGLE Vulkan backend on Adreno 6xx+/Mali-G7xx+)
    - #bare-gaming-webgl-desync    (Low-latency desynchronized WebGL2 swapchain)
    - #bare-gaming-unbuffered-touch (Request unbuffered MotionEvent dispatch)
*/
