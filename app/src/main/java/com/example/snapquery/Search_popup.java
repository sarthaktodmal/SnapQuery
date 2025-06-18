package com.example.snapquery;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class Search_popup {
    private final WindowManager windowManager;
    private final View searchView;
    public boolean isShowing;
    private final WebView webView;
    WindowManager.LayoutParams params;
    private Context context;

    public Search_popup(Context context, int width, int height) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.searchView = LayoutInflater.from(context).inflate(R.layout.search_popup, null);
        isShowing = false;
        webView = searchView.findViewById(R.id.webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Pixel 4 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.97 Mobile Safari/537.36");
        webView.getSettings().setUseWideViewPort(true);
        WebView.setWebContentsDebuggingEnabled(true);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setWebViewClient(new WebViewClient());

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.x = 0;
        params.y = (int) (height * 0.25);
        params.width = (int) (width * 0.95);
        params.height = (int) (height * 0.4);

        params.gravity = Gravity.CENTER;
    }

    public void googleSearch(String searchQuery) {
        String googleSearchUrl = "https://www.google.com/search?q=" + searchQuery;
        webView.loadUrl(googleSearchUrl);
    }

    public void showPopup() {
        if (!isShowing) {
            windowManager.addView(searchView, params);
            isShowing = true;
        }
    }

    public void removePopup() {
        if (isShowing) {
            windowManager.removeView(searchView);
            isShowing = false;
        }
    }
    public void loadURL(String url){
        webView.loadUrl(url);
    }

    public void chatGPTSearch(String recognizedText) {
        if (recognizedText == null || recognizedText.equals(" ")) {
            System.out.println("NULL TEXT");
            return;
        }
        webView.requestFocus();
        //chatGPT text area: prompt-textarea
        //google textarea:XSqSsc
        webView.evaluateJavascript("document.getElementById('prompt-textarea').focus();", value -> {
            System.out.println("Text Area Focused...");
            typeText(recognizedText);
            System.out.println("Typed now clicking send...");
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("document.querySelector(\"[data-testid='send-button']\").click();", value ->{
                        new FloatingToast(context).showToast("Done");
                    });
                }
            }, 2000);
        });
    }

    private void typeText(String recognizedText) {
        try {
            if(recognizedText != null) {
                char[] szRes = recognizedText.toCharArray(); // Convert String to Char array

                KeyCharacterMap CharMap;
                CharMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

                KeyEvent[] events = CharMap.getEvents(szRes);

                for (KeyEvent ke : events)
                    webView.dispatchKeyEvent(ke); // MainWebView is webview
            }else{System.out.println("recognizedText is null");}
        }catch (Exception e) {
            System.out.println("Exception at CHATGPT search: "+e);
        }
    }
    public void Destroy(){
        if (windowManager != null) {
            if (searchView != null) {
                if(isShowing) {
                    windowManager.removeView(searchView);
                }
            }
        }
    }
}
