package com.example.snapquery;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;

import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.List;

public class MyForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    public TextRecognizer textRecognizer;

    private FloatingToast floatingToast;
    public Search_popup search_popup;
    public Search_popup search_popup_chatgpt;

    //for taking ss more than once (keep service on untill app is closed)
    public class MyBinder extends Binder {
        MyForegroundService getService() {
            return MyForegroundService.this;
        }
    }
    private final IBinder binder = new MyBinder();
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    //main
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            createNotificationChannel();
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            floatingToast = new FloatingToast(this);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Screen Capture \uD83D\uDCF8")
                    .setContentText("SnapQuery Screen Capture Service is Running")
                    .setSmallIcon(R.drawable.notification_icon)
                    .build();

            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            System.out.println("Service Started");
            if (intent != null) {
                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
                Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);


                if (resultCode == -1 && data != null) {
                    mediaProjection = ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(resultCode, data);

                    Handler handler = new Handler(Looper.getMainLooper());
                    // Register the callback with the handler
                    mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            // Code to execute on the main thread when media projection stops
                            stopForegroundService();
                        }
                    }, handler);

                    DisplayMetrics metrics = new DisplayMetrics();
                    WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                    Display display = windowManager.getDefaultDisplay();
                    display.getRealMetrics(metrics);
                    // Get screen width and height excluding system UI elements
                    int width = metrics.widthPixels;
                    int height = metrics.heightPixels;

                    search_popup = new Search_popup(this, width, height);
                    search_popup_chatgpt = new Search_popup(this, width, height);
                    search_popup_chatgpt.loadURL("https://chat.openai.com");

                    System.out.println("test 1: " + width + ", " + height);
                    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2); // RGBX_8888 format

                    // Create a VirtualDisplay
                    mediaProjection.createVirtualDisplay("Screenshot",
                            width, height, getResources().getDisplayMetrics().densityDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, imageReader.getSurface(), null, null);
                }
            }
        }catch(Exception e){
            System.out.println(e);
        }
        return START_NOT_STICKY;
    }

    Rect selectionRect;
    public void captureScreenshot(Rect selectionRect) {
        this.selectionRect = selectionRect;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                captureScreenshotInternal();
            }
        }, 200); // Delay the capture by 1 second
    }

    private void captureScreenshotInternal() {
        Image image = imageReader.acquireNextImage();
        //System.out.println("inside captureScreenshot");
        if (image != null) {
            Bitmap bitmap = imageToBitmap(image);
            if(bitmap != null) {
                System.out.println("Bitmap Obtained!!! ");
                handleSelectedArea(bitmap);
            }
            image.close();
            stopSelf();
        }
    }
    private Bitmap imageToBitmap(Image image) {
        // Convert Image to Bitmap
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = image.getPlanes()[0].getPixelStride();
        int rowStride = image.getPlanes()[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        return bitmap;
    }

    private void handleSelectedArea(Bitmap screenshot) {
        try {
            System.out.println("Handling screenshot");

            //save screenshot
            Bitmap selectedBitmap = Bitmap.createBitmap(screenshot, selectionRect.left, selectionRect.top,
                    selectionRect.width(), selectionRect.height());

            recognizeText(selectedBitmap);
        }catch(Exception e){
            System.out.println(e);
        }
    }

    public void recognizeText(Bitmap bitmap){
        try{
            InputImage im = InputImage.fromBitmap(bitmap, 0);
            Task<Text> result = textRecognizer.process(im)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {
                            StringBuilder recognizedTextBuilder = new StringBuilder();
                            List<Text.TextBlock> blocks = text.getTextBlocks();
                            for (Text.TextBlock block : blocks) {
                                List<Text.Line> lines = block.getLines();
                                for (Text.Line line : lines) {
                                    List<Text.Element> elements = line.getElements();
                                    for (Text.Element element : elements) {
                                        recognizedTextBuilder.append(element.getText()).append(" ");
                                    }
                                }
                            }

                            String recognizedText = recognizedTextBuilder.toString();
                            System.out.println(recognizedText);

                            floatingToast.showToast(recognizedText);

                            if(MainActivity.MODE_OF_OPERATION == 1){
                                copyToClipboard(recognizedText);
                            }else if(MainActivity.MODE_OF_OPERATION == 2){
                                search_popup.googleSearch(recognizedText);
                            }else if(MainActivity.MODE_OF_OPERATION == 3){
                                System.out.println("Asking ChatGPT...");
                                search_popup_chatgpt.chatGPTSearch(MainActivity.StarterText+" "+recognizedText);
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(getApplicationContext(), "Recognition failed: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    public void copyToClipboard(String str){
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // Create a ClipData object to hold the text
        ClipData clip = ClipData.newPlainText("label", str);
        // Set the ClipData to the clipboard
        clipboard.setPrimaryClip(clip);
    }


    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }
    private void stopForegroundService() {
        stopForeground(true);
        stopSelf();
    }
    public void onDestroy() {
        super.onDestroy();
        if (mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(null);
                mediaProjection.stop();
            }catch (Exception e){
                System.out.println(e);
            }
        }
        search_popup_chatgpt.Destroy();
        search_popup.Destroy();
    }
}