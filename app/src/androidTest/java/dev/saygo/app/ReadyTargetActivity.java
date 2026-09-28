package dev.saygo.app;

import android.app.Activity;
import android.provider.Settings;

/** Test-only readiness for both animated and animation-disabled Activity launches. */
public abstract class ReadyTargetActivity extends Activity {
    private boolean ready;

    protected abstract void onTargetReady();

    private void markReady() {
        if (!ready) {
            ready = true;
            onTargetReady();
        }
    }

    @Override public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        markReady();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // With no window/transition animation, Android may omit the completion
        // callback. Wait for focus and one frame instead of waiting for an animation.
        if (hasFocus && Settings.Global.getFloat(getContentResolver(),
                Settings.Global.WINDOW_ANIMATION_SCALE, 1f) == 0f
                && Settings.Global.getFloat(getContentResolver(),
                Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f) {
            getWindow().getDecorView().postOnAnimation(() -> {
                if (hasWindowFocus()) markReady();
            });
        }
    }
}
