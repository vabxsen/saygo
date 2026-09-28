package dev.saygo.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Unlabelled canvas in a separate process; reports real touch delivery. */
public class GridTargetActivity extends ReadyTargetActivity {
    private TextView result;
    @Override protected void onTargetReady() {
        result.setText("Grid receiver ready");
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        result = new TextView(this);
        result.setText("Grid receiver opening");
        View canvas = new View(this) {
            private boolean held;
            private float x, y;
            {
                setClickable(true);
                setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                setOnLongClickListener(v -> { held = true; return true; });
            }
            @Override protected void onDraw(Canvas canvas) { canvas.drawColor(Color.rgb(246, 248, 253)); }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    held = false; x = event.getRawX(); y = event.getRawY();
                }
                boolean handled = super.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    result.setText((held ? "Hold" : "Tap") + " at " + Math.round(x) + "," + Math.round(y));
                }
                return handled;
            }
            @Override public boolean performClick() { return super.performClick(); }
        };
        frame.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(result, new FrameLayout.LayoutParams(-2, -2));
        setContentView(frame);
    }
}
