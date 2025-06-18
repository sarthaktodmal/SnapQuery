package com.example.snapquery;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity{
    private static final int REQUEST_CODE = 101;
    private static final int PROJECTION_REQUEST_CODE = 102;
    private WindowManager windowManager;
    private WindowManager windowManager2;
    private WindowManager windowManager3;
    public static int MODE_OF_OPERATION = 0;

    private View floatingView;
    private View floatingView2;
    private View floatingView3;
    private SelectionView selectionView;
    boolean isStart = false;
    public TextRecognizer textRecognizer;
    public static String StarterText = "";

    private MyForegroundService myForegroundService;
    private void bindToForegroundService() {
        Intent serviceIntent = new Intent(this, MyForegroundService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    // Service connection to get the service instance
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyForegroundService.MyBinder binder = (MyForegroundService.MyBinder) service;
            myForegroundService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            myForegroundService = null;
        }
    };
    // Unbind from the service in activity's onStop or onDestroy method
    private void unbindFromForegroundService() {
        if (myForegroundService != null) {
            unbindService(serviceConnection);
        }
    }
    // method to trigger the capture from the MainActivity
    private void triggerCapture(Rect selectionRect) {
        if (myForegroundService != null) {
            myForegroundService.captureScreenshot(selectionRect);
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        bindToForegroundService();
    }
    private void stopForegroundService() {
        Intent serviceIntent = new Intent(this, MyForegroundService.class);
        stopService(serviceIntent);
        unbindFromForegroundService();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        TextView tv = findViewById(R.id.editTextInput);
        tv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                StarterText = s.toString();
                //System.out.println(StarterText);
            }
        });

        if (!Settings.canDrawOverlays(this)) {
            // If not granted, request the permission
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
        }

    }

    private void askNotificationPermission(){
        new AlertDialog.Builder(this)
                .setTitle("Enable Notifications")
                .setMessage("Please enable notifications for SnapQuery.")
                .setPositiveButton("OK", (dialog, which) -> {
                    // Redirect the user to the app settings to enable notifications
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                    intent.setData(uri);
                    this.startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Handle the cancellation if needed
                    dialog.dismiss();
                }).show();
    }
    private void askMediaProjectionPermission(){
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), PROJECTION_REQUEST_CODE);
    }
    int result_Code = 0;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        result_Code = resultCode;
        if (requestCode == PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // MediaProjection permission granted, start the service to capture screenshot
                Intent serviceIntent = new Intent(this, MyForegroundService.class);
                serviceIntent.putExtra(MyForegroundService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(MyForegroundService.EXTRA_RESULT_DATA, data);
                startForegroundService(serviceIntent);
            } else {
                Toast.makeText(this,"Permission Denied",Toast.LENGTH_SHORT).show();
            }
        }
    }
    //
    public void startBtn(View v){
        // Check and request notification permission
        if(!NotificationManagerCompat.from(this).areNotificationsEnabled()){
            askNotificationPermission();
            return;
        }
        // Check and request SYSTEM_ALERT_WINDOW permission
        if (!Settings.canDrawOverlays(this)) {
            // If not granted, request the permission
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
            return;
        }
        if(!isStart && Settings.canDrawOverlays(this)){
            if(result_Code != RESULT_OK) askMediaProjectionPermission();
            if(result_Code != RESULT_OK) return;
            // Permission granted, create floating window and selection view
            createFloatingWindow();
            createSwitchingFloatingWindow();
            createToggleFloatingWindow();
            isStart = true;
        }
    }
    public void createFloatingWindow() {
        // Initialize WindowManager
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Initialize floating button
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 300;
        params.width = getResources().getDisplayMetrics().widthPixels;

        // Add the floating button to the window
        windowManager.addView(floatingView, params);

        // Initialize selection view
        selectionView = new SelectionView(this);
        selectionView.setParams(params);
        selectionView.setVisibility(View.GONE);
        windowManager.addView(selectionView, params);

        //Set click listener for the floating button
        ImageView floatingButton = floatingView.findViewById(R.id.floating_button);
        floatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // show selection view when the floating button is clicked
                selectionView.setVisibility(View.VISIBLE);
            }
        });
    }


    public void createSwitchingFloatingWindow() {
        windowManager2 = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Initialize floating button
        floatingView2 = LayoutInflater.from(this).inflate(R.layout.layout_floating_button_color, null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 500;

        // Add the floating button to the window
        windowManager2.addView(floatingView2, params);

        // Set click listener for the floating button
        ImageView floatingButton2 = floatingView2.findViewById(R.id.color_circle);
        floatingButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GradientDrawable shapeDrawable = (GradientDrawable) getResources().getDrawable(R.drawable.rounded_corners_color_buttons, null);
                if(MODE_OF_OPERATION < 3){
                    MODE_OF_OPERATION++;
                }else{
                    MODE_OF_OPERATION = 0;
                }
                switch (MODE_OF_OPERATION){
                    case 0: // only show toast
                        shapeDrawable.setColor(Color.argb(0.1f,0.0f,0.0f,0.0f));
                        floatingButton2.setBackground(shapeDrawable);
                        break;
                    case 1: // show toast as well as copy to clipboard
                        shapeDrawable.setColor(Color.argb(0.1f,1.0f,0.0f,0.0f));
                        floatingButton2.setBackground(shapeDrawable);
                        break;
                    case 2:
                        shapeDrawable.setColor(Color.argb(0.1f,0.0f,0.0f,1.0f));
                        floatingButton2.setBackground(shapeDrawable);
                        break;
                    case 3:
                        shapeDrawable.setColor(Color.argb(0.1f,0.0f,1.0f,0.0f));
                        floatingButton2.setBackground(shapeDrawable);
                        break;
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public void createToggleFloatingWindow() {
        // Initialize WindowManager
        windowManager3 = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Initialize floating button
        floatingView3 = LayoutInflater.from(this).inflate(R.layout.floating_show_layout, null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 600;

        // Add the floating button to the window
        windowManager3.addView(floatingView3, params);

        // Set click listener for the floating button
        ImageView floatingButton3 = floatingView3.findViewById(R.id.show_oval_layout);

        floatingButton3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(MODE_OF_OPERATION == 2){
                    if(myForegroundService.search_popup.isShowing){
                        myForegroundService.search_popup.removePopup();
                    }else{
                        myForegroundService.search_popup.showPopup();
                    }
                }else if(MODE_OF_OPERATION == 3){
                    if(myForegroundService.search_popup_chatgpt.isShowing){
                        myForegroundService.search_popup_chatgpt.removePopup();
                    }else{
                        myForegroundService.search_popup_chatgpt.showPopup();
                    }
                }
            }
        });
    }

    private class SelectionView extends View {
        private Paint paint;
        private boolean isSelecting;
        private Rect selectionRect;
        private Rect selectionRect2;
        private WindowManager.LayoutParams params;

        public SelectionView(Context context) {
            super(context);
            paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            selectionRect = new Rect();
            selectionRect2 = new Rect();
        }
        public void setParams(WindowManager.LayoutParams params) {
            this.params = params;
        }
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (isSelecting) {
                canvas.drawRect(selectionRect2, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    selectionRect.left = (int) event.getRawX();
                    selectionRect.top = (int) event.getRawY();
                    selectionRect.right = (int) event.getRawX();
                    selectionRect.bottom = (int) event.getRawY();

                    selectionRect2.left = (int) event.getX();
                    selectionRect2.top = (int) event.getY();
                    selectionRect2.right = (int) event.getX();
                    selectionRect2.bottom = (int) event.getY();
                    isSelecting = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    selectionRect.right = (int) event.getRawX();
                    selectionRect.bottom = (int) event.getRawY();

                    selectionRect2.right = (int) event.getX();
                    selectionRect2.bottom = (int) event.getY();
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    isSelecting = false;
                    triggerCapture(selectionRect);
                    selectionView.setVisibility(View.GONE);
                    break;
            }
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (windowManager != null) {
            if (floatingView != null) {
                windowManager.removeView(floatingView);
            }
            if (selectionView != null) {
                windowManager.removeView(selectionView);
            }
        }
        if (windowManager2 != null) {
            if (floatingView2 != null) {
                windowManager2.removeView(floatingView2);
            }
        }
        if (windowManager3 != null) {
            if (floatingView3 != null) {
                windowManager3.removeView(floatingView3);
            }
        }
        stopForegroundService();
    }
}