package com.mybrowser;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private BrowserWebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ExtensionManager extensionManager;
    private YouTubeExtractor youtubeExtractor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize components
        extensionManager = new ExtensionManager(this);
        youtubeExtractor = new YouTubeExtractor();
        
        // Create browser WebView
        webView = new BrowserWebView(this);
        setupWebView();
        
        setContentView(webView);
        
        // Load extensions
        if (BuildConfig.ENABLE_EXTENSIONS) {
            extensionManager.loadBundledExtensions();
        }
        
        // Load home page
        webView.loadUrl("https://www.google.com");
    }
    
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        
        // Basic settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Advanced settings
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // Enable DevTools
        if (BuildConfig.ENABLE_DEVTOOLS) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        
        // Custom User Agent with extension support indicator
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua + " MyBrowser/1.0 Extensions/Enabled");
        
        // WebViewClient
        webView.setWebViewClient(new BrowserWebViewClient(this));
        
        // WebChromeClient with DevTools and extension support
        webView.setWebChromeClient(new BrowserChromeClient(this));
        
        // Add JavaScript interface for extensions
        webView.addJavascriptInterface(new BrowserInterface(), "Browser");
    }
    
    // Back button handling
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
    
    // Inner classes
    private class BrowserWebViewClient extends android.webkit.WebViewClient {
        private Activity activity;
        
        BrowserWebViewClient(Activity activity) {
            this.activity = activity;
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Let extensions handle URL if needed
            if (extensionManager.shouldInterceptUrl(url)) {
                return true;
            }
            view.loadUrl(url);
            return true;
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            
            // Inject extension scripts
            if (BuildConfig.ENABLE_EXTENSIONS) {
                extensionManager.injectScripts(view, url);
            }
            
            // Auto-extract YouTube links
            if (url.contains("youtube.com") || url.contains("youtu.be")) {
                youtubeExtractor.extractFromWebView(view);
            }
        }
    }
    
    private class BrowserChromeClient extends WebChromeClient {
        private Activity activity;
        
        BrowserChromeClient(Activity activity) {
            this.activity = activity;
        }
        
        // Fullscreen support
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            ((FrameLayout) activity.getWindow().getDecorView()).addView(view, 
                new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        
        @Override
        public void onHideCustomView() {
            ((FrameLayout) activity.getWindow().getDecorView()).removeView(customView);
            customView = null;
            customViewCallback.onCustomViewHidden();
        }
        
        // Console logging (DevTools)
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            System.out.println(consoleMessage.message() + " -- From: " 
                + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
            return true;
        }
        
        // Geolocation
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, 
                GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }
        
        // Permissions (camera, mic, etc.)
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.grant(request.getResources());
        }
        
        // File upload
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<String[]> filePathCallback, 
                FileChooserParams fileChooserParams) {
            // Handle file upload
            return true;
        }
        
        // Progress updates
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            activity.setTitle("Loading... " + newProgress + "%");
            if (newProgress == 100) {
                activity.setTitle(view.getTitle());
            }
        }
    }
    
    private class BrowserInterface {
        @JavascriptInterface
        public void extractYouTubeLinks() {
            runOnUiThread(() -> {
                youtubeExtractor.extractFromWebView(webView);
            });
        }
        
        @JavascriptInterface
        public void installExtension(String extensionJson) {
            runOnUiThread(() -> {
                extensionManager.installFromJson(extensionJson);
            });
        }
        
        @JavascriptInterface
        public void showNotification(String message) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }
    }
}
