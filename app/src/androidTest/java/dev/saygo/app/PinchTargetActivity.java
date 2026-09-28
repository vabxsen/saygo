package dev.saygo.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Locale;

/** Receives real two-finger events and runs Android's native scale detector. */
public class PinchTargetActivity extends ReadyTargetActivity {
    private TextView result;
    @Override protected void onTargetReady() {
        result.setText("Pinch receiver ready");
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        result = new TextView(this);
        result.setText("Pinch receiver opening");
        View canvas = new View(this) {
            private float scale = 1f;
            private int pointers, downs, ups;
            private final ScaleGestureDetector detector = new ScaleGestureDetector(PinchTargetActivity.this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        scale *= detector.getScaleFactor();
                        return true;
                    }
                });
            @Override public boolean onTouchEvent(MotionEvent event) {
                pointers = Math.max(pointers, event.getPointerCount());
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) downs++;
                if (event.getActionMasked() == MotionEvent.ACTION_UP) ups++;
                detector.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    result.setText(String.format(Locale.ROOT, "Zoom=%.3f pointers=%d complete=%s down=%d up=%d",
                        scale, pointers, event.getActionMasked() == MotionEvent.ACTION_UP, downs, ups));
                    android.util.Log.i("PinchReceipt", result.getText() + " minSpan=" + android.view.ViewConfiguration.get(PinchTargetActivity.this).getScaledMinimumScalingSpan() + " density=" + getResources().getDisplayMetrics().density);
                }
                return true;
            }
        };
        canvas.setBackgroundColor(0xfff6f8fd);
        canvas.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(result, new FrameLayout.LayoutParams(-2, -2));
        setContentView(frame);
        if (getIntent().getBooleanExtra("offset", false)) {
            android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            boolean wide = getIntent().getBooleanExtra("wide", false);
            params.x = wide ? getResources().getDisplayMetrics().widthPixels / 20 : 80; params.y = 160;
            params.width = getResources().getDisplayMetrics().widthPixels * (wide ? 9 : 3) / (wide ? 10 : 4);
            params.height = wide ? Math.max((int)(96 * getResources().getDisplayMetrics().density), getResources().getDisplayMetrics().heightPixels / 5) : getResources().getDisplayMetrics().heightPixels * 2 / 3;
            getWindow().setAttributes(params);
        }
    }
}
