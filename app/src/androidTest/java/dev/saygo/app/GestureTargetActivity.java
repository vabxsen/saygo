package dev.saygo.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/** Separate test-APK window; uses only framework classes, with no app runtime dependency. */
public class GestureTargetActivity extends ReadyTargetActivity {
    private TextView label;
    @Override protected void onTargetReady() {
        label.setText("Gesture target: ready");
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        label = new TextView(this);
        label.setText("Gesture target: opening");
        label.setGravity(Gravity.CENTER);
        label.setTextSize(24f);
        label.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    label.setText("Gesture target: down");
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    String direction = Math.abs(dx) > Math.abs(dy)
                        ? (dx > 0 ? "RIGHT" : "LEFT") : (dy > 0 ? "DOWN" : "UP");
                    label.setText("Received " + direction);
                    view.performClick();
                }
                return true;
            }
        });
        setContentView(label);
    }
}
