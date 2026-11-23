// Copyright 2026 Bare Browser Gaming Extension Contributors
// Target path inside Bare Browser Chromium checkout:
// chrome/android/java/src/org/chromium/chrome/browser/gaming/BareGamingModeController.java

package org.chromium.chrome.browser.gaming;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.jni_zero.CalledByNative;
import org.jni_zero.JNINamespace;
import org.jni_zero.NativeMethods;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.content_public.browser.WebContents;

/**
 * Native Chromium Gaming Mode controller for Bare Browser (Android).
 *
 * Explicitly separates:
 *  1. Requested Display Refresh Rate (Android Display.Mode / Surface.setFrameRate)
 *  2. Android UI/Compositor Presentation Rate (Window.OnFrameMetricsAvailableListener)
 *  3. Actual WebGL/Viz Game Frame Swap Rate (reported from native Viz/Blink via JNI)
 */
@JNINamespace("bare_gaming")
public class BareGamingModeController {

    public static final class TelemetrySnapshot {
        /** Display refresh rate requested via Window/Display.Mode (e.g., 120.0 Hz). */
        public final float requestedDisplayHz;
        /** Actual display refresh rate currently active on the panel. */
        public final float activeDisplayHz;
        /** Actual game/WebGL swap rate reported by native Viz/Blink (distinct from display Hz). */
        public final float actualGameSwapFps;
        /** 95th-percentile frame total duration from Android FrameMetrics (ms). */
        public final float p95FrameDurationMs;
        /** Active Chromium GPU backend string from GpuDataManagerImpl (e.g. ANGLE/Vulkan). */
        public final String gpuBackend;
        /** Number of background tabs frozen to protect active Wasm/WebGL memory & CPU. */
        public final int frozenBackgroundTabs;

        public TelemetrySnapshot(
                float requestedDisplayHz,
                float activeDisplayHz,
                float actualGameSwapFps,
                float p95FrameDurationMs,
                String gpuBackend,
                int frozenBackgroundTabs) {
            this.requestedDisplayHz = requestedDisplayHz;
            this.activeDisplayHz = activeDisplayHz;
            this.actualGameSwapFps = actualGameSwapFps;
            this.p95FrameDurationMs = p95FrameDurationMs;
            this.gpuBackend = gpuBackend;
            this.frozenBackgroundTabs = frozenBackgroundTabs;
        }
    }

    public interface TelemetryListener {
        void onTelemetryUpdated(@NonNull TelemetrySnapshot snapshot);
    }

    private final Activity mActivity;
    private final TabModelSelector mTabModelSelector;
    private final View mCompositorViewHolder;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private long mNativeBridgePtr;
    private boolean mGamingModeEnabled;
    private float mRequestedDisplayHz = 60.0f;
    private volatile float mActualGameSwapFps = 0.0f;
    private volatile float mP95FrameDurationMs = 16.6f;
    private volatile String mGpuBackend = "Probing Chromium GPU Process...";
    private int mFrozenBackgroundTabs;

    @Nullable private TelemetryListener mTelemetryListener;
    @Nullable private Window.OnFrameMetricsAvailableListener mFrameMetricsListener;

    private final float[] mFrameDurationRingMs = new float[60];
    private int mRingWriteIndex;
    private int mRingCount;

    public BareGamingModeController(
            @NonNull Activity activity,
            @NonNull TabModelSelector tabModelSelector,
            @NonNull View compositorViewHolder) {
        mActivity = activity;
        mTabModelSelector = tabModelSelector;
        mCompositorViewHolder = compositorViewHolder;
        mNativeBridgePtr = BareGamingModeControllerJni.get().init(this);
    }

    public void setTelemetryListener(@Nullable TelemetryListener listener) {
        mTelemetryListener = listener;
    }

    public boolean isGamingModeEnabled() {
        return mGamingModeEnabled;
    }

    /**
     * Enables or disables native Chromium Gaming Mode for the active tab.
     *
     * @param enabled Whether Gaming Mode is active.
     * @param targetRenderScalePercent 70..100 effective WebGL backbuffer scale (100 = native).
     * @param requestMaxRefreshRate Request device's highest Display.Mode refresh rate.
     * @param freezeBackgroundTabs Freeze non-audible background RenderProcessHosts.
     * @param bypassExtensionFilterForOrigin Avoid uBlock/webRequest stalls on trusted game asset streams.
     */
    public void setGamingModeEnabled(
            boolean enabled,
            int targetRenderScalePercent,
            boolean requestMaxRefreshRate,
            boolean freezeBackgroundTabs,
            boolean bypassExtensionFilterForOrigin) {
        mGamingModeEnabled = enabled;
        applyAndroidWindowAndDisplayMode(enabled, requestMaxRefreshRate);

        Tab activeTab = mTabModelSelector.getCurrentTab();
        WebContents activeContents = activeTab != null ? activeTab.getWebContents() : null;

        mFrozenBackgroundTabs = 0;
        if (mNativeBridgePtr != 0 && activeContents != null) {
            if (enabled && freezeBackgroundTabs) {
                mFrozenBackgroundTabs = freezeInactiveTabs(activeTab);
            } else {
                unfreezeAllTabs();
            }

            BareGamingModeControllerJni.get().setGamingModeForActiveContents(
                    mNativeBridgePtr,
                    activeContents,
                    enabled,
                    Math.max(70, Math.min(100, targetRenderScalePercent)),
                    bypassExtensionFilterForOrigin);
        }

        if (enabled) {
            startFrameMetricsCollection();
        } else {
            stopFrameMetricsCollection();
        }
    }

    /**
     * Must be invoked from CompositorViewHolder.dispatchTouchEvent() to bypass Android VSync
     * touch coalescing when Gaming Mode is active.
     */
    public void onCompositorTouchEvent(@NonNull MotionEvent event) {
        if (mGamingModeEnabled && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mCompositorViewHolder.requestUnbufferedDispatch(event);
        }
    }

    private void applyAndroidWindowAndDisplayMode(boolean gamingEnabled, boolean requestMaxHz) {
        Window window = mActivity.getWindow();
        if (window == null) return;

        if (gamingEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        WindowManager.LayoutParams attrs = window.attributes;
        Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? mActivity.getDisplay()
                : mActivity.getWindowManager().getDefaultDisplay();

        if (display != null) {
            Display.Mode bestMode = null;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (bestMode == null || mode.getRefreshRate() > bestMode.getRefreshRate()) {
                    bestMode = mode;
                }
            }
            if (gamingEnabled && requestMaxHz && bestMode != null) {
                attrs.preferredDisplayModeId = bestMode.getModeId();
                attrs.preferredRefreshRate = bestMode.getRefreshRate();
                mRequestedDisplayHz = bestMode.getRefreshRate();
            } else {
                attrs.preferredDisplayModeId = 0;
                attrs.preferredRefreshRate = 0.0f;
                mRequestedDisplayHz = display.getRefreshRate();
            }
            window.setAttributes(attrs);

            // Android 11+ Surface.setFrameRate hint on Chromium's Compositor SurfaceView
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && mCompositorViewHolder instanceof SurfaceView) {
                Surface surface = ((SurfaceView) mCompositorViewHolder).getHolder().getSurface();
                if (surface != null && surface.isValid()) {
                    float rate = (gamingEnabled && requestMaxHz) ? mRequestedDisplayHz : 0.0f;
                    int strategy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                            : Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS;
                    surface.setFrameRate(
                            rate,
                            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            strategy);
                }
            }
        }

        // Sustained Performance Mode prevents aggressive thermal clock oscillations during long MMORPG sessions
        PowerManager pm = mActivity.getSystemService(PowerManager.class);
        if (pm != null && pm.isSustainedPerformanceModeSupported()) {
            window.setSustainedPerformanceMode(gamingEnabled);
        }

        WindowInsetsControllerCompat insets =
                WindowCompat.getInsetsController(window, window.getDecorView());
        insets.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (gamingEnabled) {
            insets.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private int freezeInactiveTabs(@NonNull Tab activeTab) {
        int frozen = 0;
        TabModel model = mTabModelSelector.getCurrentModel();
        for (int i = 0; i < model.getCount(); i++) {
            Tab tab = model.getTabAt(i);
            if (tab == null || tab.getId() == activeTab.getId()) continue;
            WebContents wc = tab.getWebContents();
            if (wc != null && !wc.isDestroyed() && !wc.isAudioMuted()) {
                BareGamingModeControllerJni.get().setBackgroundTabFrozen(mNativeBridgePtr, wc, true);
                frozen++;
            }
        }
        return frozen;
    }

    private void unfreezeAllTabs() {
        TabModel model = mTabModelSelector.getCurrentModel();
        for (int i = 0; i < model.getCount(); i++) {
            Tab tab = model.getTabAt(i);
            if (tab == null) continue;
            WebContents wc = tab.getWebContents();
            if (wc != null && !wc.isDestroyed()) {
                BareGamingModeControllerJni.get().setBackgroundTabFrozen(mNativeBridgePtr, wc, false);
            }
        }
    }

    private void startFrameMetricsCollection() {
        Window window = mActivity.getWindow();
        if (window == null || mFrameMetricsListener != null) return;

        mFrameMetricsListener = (w, frameMetrics, dropCount) -> {
            long totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
            float totalMs = totalNs / 1_000_000.0f;
            mFrameDurationRingMs[mRingWriteIndex] = totalMs;
            mRingWriteIndex = (mRingWriteIndex + 1) % mFrameDurationRingMs.length;
            if (mRingCount < mFrameDurationRingMs.length) mRingCount++;
            mP95FrameDurationMs = computeP95();
            emitSnapshot();
        };
        window.addOnFrameMetricsAvailableListener(mFrameMetricsListener, mMainHandler);
    }

    private void stopFrameMetricsCollection() {
        Window window = mActivity.getWindow();
        if (window != null && mFrameMetricsListener != null) {
            window.removeOnFrameMetricsAvailableListener(mFrameMetricsListener);
            mFrameMetricsListener = null;
        }
    }

    private float computeP95() {
        if (mRingCount == 0) return 0.0f;
        float[] copy = new float[mRingCount];
        System.arraycopy(mFrameDurationRingMs, 0, copy, 0, mRingCount);
        java.util.Arrays.sort(copy);
        int idx = Math.min(copy.length - 1, (int) Math.ceil(copy.length * 0.95) - 1);
        return copy[Math.max(0, idx)];
    }

    @CalledByNative
    private void onNativeTelemetryUpdate(float actualGameSwapFps, String gpuBackend) {
        mActualGameSwapFps = actualGameSwapFps;
        if (gpuBackend != null && !gpuBackend.isEmpty()) {
            mGpuBackend = gpuBackend;
        }
        emitSnapshot();
    }

    private void emitSnapshot() {
        TelemetryListener listener = mTelemetryListener;
        if (listener == null) return;
        Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? mActivity.getDisplay()
                : mActivity.getWindowManager().getDefaultDisplay();
        float activeHz = display != null ? display.getRefreshRate() : 60.0f;
        listener.onTelemetryUpdated(new TelemetrySnapshot(
                mRequestedDisplayHz,
                activeHz,
                mActualGameSwapFps,
                mP95FrameDurationMs,
                mGpuBackend,
                mFrozenBackgroundTabs));
    }

    public void destroy() {
        stopFrameMetricsCollection();
        if (mNativeBridgePtr != 0) {
            BareGamingModeControllerJni.get().destroy(mNativeBridgePtr);
            mNativeBridgePtr = 0;
        }
    }

    @NativeMethods
    interface Natives {
        long init(BareGamingModeController caller);
        void destroy(long nativeBareGamingModeBridge);
        void setGamingModeForActiveContents(
                long nativeBareGamingModeBridge,
                WebContents activeWebContents,
                boolean enabled,
                int renderScalePercent,
                boolean bypassExtensionFilter);
        void setBackgroundTabFrozen(
                long nativeBareGamingModeBridge,
                WebContents backgroundWebContents,
                boolean frozen);
    }
}
