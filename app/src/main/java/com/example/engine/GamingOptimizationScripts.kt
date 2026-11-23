package com.example.engine

object GamingOptimizationScripts {

    /**
     * Generates a safe, self-contained JS optimization shim injected into the active WebView.
     * Only validated primitives (booleans & clamped integers) are embedded; zero native JS bridges are exposed.
     */
    fun buildEngineOptimizerScript(
        gamingModeActive: Boolean,
        webGlHighPerformance: Boolean,
        disableAntialias: Boolean,
        renderScalePercent: Int
    ): String {
        val safeScale = renderScalePercent.coerceIn(70, 100) / 100.0
        val highPerf = if (webGlHighPerformance) "true" else "false"
        val noAa = if (disableAntialias) "true" else "false"
        val gaming = if (gamingModeActive) "true" else "false"

        return """
            (function() {
              try {
                window.__BARE_TURBO_CFG__ = {
                  gamingMode: $gaming,
                  highPerf: $highPerf,
                  disableAntialias: $noAa,
                  renderScale: $safeScale
                };

                // 1. Dynamic DevicePixelRatio scaling for GPU fragment fill-rate optimization
                if (!window.__BARE_NATIVE_DPR__) {
                  window.__BARE_NATIVE_DPR__ = window.devicePixelRatio || 1;
                }
                var targetDpr = Math.max(1.0, window.__BARE_NATIVE_DPR__ * $safeScale);
                try {
                  Object.defineProperty(window, 'devicePixelRatio', {
                    configurable: true,
                    get: function() { return targetDpr; }
                  });
                } catch (_) {}

                // 2. WebGL / Canvas2D Context Interceptor: force high-performance GPU + desynchronized low-latency path
                if (!HTMLCanvasElement.prototype.__bareTurboPatched) {
                  var origGetContext = HTMLCanvasElement.prototype.getContext;
                  HTMLCanvasElement.prototype.getContext = function(type, attrs) {
                    var cfg = window.__BARE_TURBO_CFG__ || {};
                    var nextAttrs = Object.assign({}, attrs || {});
                    if (type === 'webgl' || type === 'webgl2' || type === 'experimental-webgl') {
                      if (cfg.highPerf) {
                        nextAttrs.powerPreference = 'high-performance';
                        nextAttrs.desynchronized = true;
                        if (typeof nextAttrs.preserveDrawingBuffer === 'undefined') {
                          nextAttrs.preserveDrawingBuffer = false;
                        }
                      }
                      if (cfg.disableAntialias) {
                        nextAttrs.antialias = false;
                      }
                    } else if (type === '2d' && cfg.highPerf) {
                      nextAttrs.desynchronized = true;
                    }
                    return origGetContext.call(this, type, nextAttrs);
                  };
                  HTMLCanvasElement.prototype.__bareTurboPatched = true;
                }

                // 3. Low-Latency Touch & Input CSS Injection (eliminates 300ms double-tap delay & touch callouts)
                var styleId = '__bare_turbo_gaming_style__';
                var styleEl = document.getElementById(styleId);
                if (!styleEl) {
                  styleEl = document.createElement('style');
                  styleEl.id = styleId;
                  (document.head || document.documentElement).appendChild(styleEl);
                }
                if ($gaming) {
                  styleEl.textContent =
                    'html, body { overscroll-behavior: none !important; touch-action: manipulation; }' +
                    'canvas { touch-action: none !important; -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; will-change: transform; }';
                } else {
                  styleEl.textContent = 'canvas { touch-action: manipulation; }';
                }
              } catch (_) {}
            })();
        """.trimIndent()
    }
}
