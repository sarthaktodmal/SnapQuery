package com.example.snapquery;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingToast {
    private final WindowManager windowManager;
    private final View toastView;
    private boolean isShowing;

    public FloatingToast(Context context) {
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.toastView = LayoutInflater.from(context).inflate(R.layout.floating_toast_layout, null);
        this.isShowing = false;
    }

    public void showToast(String message) {
        if (!isShowing) {
            TextView textView = toastView.findViewById(R.id.toast_message);
            textView.setText(message);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            //params.gravity = Gravity.START | Gravity.TOP; // Set gravity to top-left corner
            params.x = 0;
            params.y = 400;

            params.gravity = Gravity.CENTER;


            windowManager.addView(toastView, params);
            isShowing = true;

            // Remove the toast after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    removeToast();
                }
            }, 3000);
        }
    }
    public void removeToast() {
        if (isShowing) {
            windowManager.removeView(toastView);
            isShowing = false;
        }
    }
}