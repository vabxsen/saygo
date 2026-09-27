package dev.saygo.app;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Locale;

/** Separate app: native drag-and-drop or raw continuous-pointer evidence. */
public class DragTargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean nativeDrag = getIntent().getBooleanExtra("native", false);
        FrameLayout frame = new FrameLayout(this);
        TextView result = new TextView(this);
        result.setText("Drag receiver ready");
        View canvas = new View(this) {
            private float startX, startY, lastX, lastY, distance;
            private long downTime, firstMove = -1;
            private int downs, ups;
            private boolean held;
            {
                setClickable(true);
                setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                setOnLongClickListener(v -> {
                    held = true;
                    if (nativeDrag) {
                        boolean started = startDragAndDrop(ClipData.newPlainText("test", "saygo drag"), new View.DragShadowBuilder(this) {
                            @Override public void onProvideShadowMetrics(Point size, Point touch) { size.set(20, 20); touch.set(10, 10); }
                            @Override public void onDrawShadow(Canvas shadow) { shadow.drawColor(0xff084bdd); }
                        }, null, 0);
                        if (!started) result.setText("Native drag failed to start");
                    }
                    return true;
                });
                setOnDragListener((v, event) -> {
                    if (event.getAction() == DragEvent.ACTION_DROP) {
                        int[] location = new int[2]; getLocationOnScreen(location);
                        result.setText(String.format(Locale.ROOT, "Native drop from %.0f,%.0f to %.0f,%.0f held=%s payload=%s",
                            startX, startY, event.getX()+location[0], event.getY()+location[1], held,
                            event.getClipData().getItemAt(0).getText()));
                        android.util.Log.i("DragReceipt", result.getText().toString());
                    }
                    return true;
                });
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downs++; startX = event.getRawX(); startY = event.getRawY();
                    downTime = android.os.SystemClock.uptimeMillis();
                    sendBroadcast(new android.content.Intent("dev.saygo.app.test.DRAG_DOWN").setPackage("dev.saygo.app"));
                    result.setText("Drag down");
                }
                lastX = event.getRawX(); lastY = event.getRawY();
                distance = Math.max(distance, (float)Math.hypot(lastX-startX, lastY-startY));
                if (distance > 3 && firstMove < 0) firstMove = android.os.SystemClock.uptimeMillis()-downTime;
                boolean handled = super.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !nativeDrag) {
                    ups++;
                    result.setText(String.format(Locale.ROOT, "Raw drag from %.0f,%.0f to %.0f,%.0f held=%s down=%d up=%d firstMove=%d distance=%.0f",
                        startX, startY, lastX, lastY, held, downs, ups, firstMove, distance));
                    android.util.Log.i("DragReceipt", result.getText().toString());
                }
                return handled;
            }
            @Override public boolean performClick() { return super.performClick(); }
        };
        canvas.setBackgroundColor(0xfff6f8fd);
        frame.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(result, new FrameLayout.LayoutParams(-2, -2));
        setContentView(frame);
        if (getIntent().getBooleanExtra("offset", false)) {
            android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            params.x = 30; params.y = 60;
            params.width = getResources().getDisplayMetrics().widthPixels * 3 / 4;
            params.height = getResources().getDisplayMetrics().heightPixels * 2 / 3;
            getWindow().setAttributes(params);
        }
    }
}
