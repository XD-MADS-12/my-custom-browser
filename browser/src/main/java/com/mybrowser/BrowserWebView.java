package com.mybrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom WebView with Extensions, DevTools & YouTube Extractor Support
 * Kiwi Browser এর মতো ফিচার সহ
 */
public class BrowserWebView extends WebView {

    private static final String TAG = "BrowserWebView";
    private static final String USER_AGENT_SUFFIX = " MyBrowserPro/1.0 (Extensions/Enabled; DevTools/Enabled)";

    // Callback interfaces
    private OnPageLoadListener pageLoadListener;
    private OnYouTubeLinksFoundListener youtubeLinksListener;
    private OnExtensionMessageListener extensionMessageListener;

    // State
    private boolean isPageLoaded = false;
    private int loadProgress = 0;
    private String currentUrl = "";
    private String currentTitle = "";
    private List<String> youtubeLinks = new ArrayList<>();
    private Map<String, Boolean> blockedDomains = new HashMap<>();
    private List<String> extensionScripts = new ArrayList<>();
    private boolean adBlockEnabled = true;
    private boolean darkModeEnabled = false;

    // Gesture detector for custom gestures
    private GestureDetector gestureDetector;

    // Interfaces
    public interface OnPageLoadListener {
        void onPageStarted(String url);
        void onPageFinished(String url);
        void onProgressChanged(int progress);
        void onReceivedTitle(String title);
        void onReceivedError(String error);
    }

    public interface OnYouTubeLinksFoundListener {
        void onLinksFound(List<String> links);
    }

    public interface OnExtensionMessageListener {
        void onMessage(String extensionId, String message);
    }

    // Constructors
    public BrowserWebView(Context context) {
        super(context);
        init(context);
    }

    public BrowserWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BrowserWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init(Context context) {
        // ===== BASIC SETTINGS =====
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // ===== ADVANCED SETTINGS =====
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setSupportZoom(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setEnableSmoothTransition(true);

        // ===== HARDWARE ACCELERATION =====
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // ===== DEVTOOLS ENABLE =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setWebContentsDebuggingEnabled(true);
            Log.d(TAG, "DevTools Enabled - Access via chrome://inspect");
        }

        // ===== CUSTOM USER AGENT =====
        String defaultUA = settings.getUserAgentString();
        settings.setUserAgentString(defaultUA + USER_AGENT_SUFFIX);

        // ===== COOKIE MANAGER =====
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(this, true);

        // ===== DOWNLOAD LISTENER =====
        setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Log.d(TAG, "Download triggered: " + url);
            // Handle download - can be passed to DownloadManager
            if (pageLoadListener != null) {
                // Notify activity about download
            }
        });

        // ===== GESTURE DETECTOR =====
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Double tap to zoom
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (velocityX > 1000) {
                    // Swipe right - go back
                    if (canGoBack()) goBack();
                    return true;
                } else if (velocityX < -1000) {
                    // Swipe left - go forward
                    if (canGoForward()) goForward();
                    return true;
                }
                return false;
            }
        });

        // ===== SETUP CLIENTS =====
        setupWebViewClient();
        setupWebChromeClient();

        // ===== JAVASCRIPT INTERFACE =====
        addJavascriptInterface(new BrowserJSInterface(), "AndroidBrowser");
        addJavascriptInterface(new YouTubeExtractorJS(), "YouTubeExtractor");

        Log.d(TAG, "BrowserWebView initialized successfully");
    }

    // ===== WEB VIEW CLIENT =====
    private void setupWebViewClient() {
        setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                isPageLoaded = false;
                currentUrl = url;
                youtubeLinks.clear();
                Log.d(TAG, "Page started: " + url);
                
                if (pageLoadListener != null) {
                    pageLoadListener.onPageStarted(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isPageLoaded = true;
                currentUrl = url;
                Log.d(TAG, "Page finished: " + url);

                // Inject extension scripts
                injectExtensionScripts();

                // Extract YouTube links
                extractYouTubeLinks();

                // Apply dark mode if enabled
                if (darkModeEnabled) {
                    applyDarkMode();
                }

                if (pageLoadListener != null) {
                    pageLoadListener.onPageFinished(url);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "Should override: " + url);
                
                // Check if extension wants to handle this URL
                if (handleExtensionUrl(url)) {
                    return true;
                }

                // Handle special schemes
                if (url.startsWith("tel:") || url.startsWith("mailto:") || 
                    url.startsWith("sms:") || url.startsWith("geo:")) {
                    // Let system handle these
                    return false;
                }

                view.loadUrl(url);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Ad blocking
                if (adBlockEnabled && isAdDomain(url)) {
                    Log.d(TAG, "Blocked ad: " + url);
                    return new WebResourceResponse(
                        "text/plain", 
                        "utf-8", 
                        new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
                    );
                }

                // Tracker blocking
                if (isTrackerDomain(url)) {
                    Log.d(TAG, "Blocked tracker: " + url);
                    return new WebResourceResponse(
                        "text/plain", 
                        "utf-8", 
                        new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
                    );
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, 
                    String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "Error: " + description + " at " + failingUrl);
                
                if (pageLoadListener != null) {
                    pageLoadListener.onReceivedError(description);
                }
            }
        });
    }

    // ===== WEB CHROME CLIENT =====
    private void setupWebChromeClient() {
        setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                loadProgress = newProgress;
                Log.d(TAG, "Progress: " + newProgress + "%");
                
                if (pageLoadListener != null) {
                    pageLoadListener.onProgressChanged(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                currentTitle = title;
                
                if (pageLoadListener != null) {
                    pageLoadListener.onReceivedTitle(title);
                }
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                // Handle favicon
            }

            // ===== CONSOLE LOGGING (DevTools) =====
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String message = String.format("[%s:%d] %s: %s",
                    consoleMessage.sourceId(),
                    consoleMessage.lineNumber(),
                    consoleMessage.messageLevel().name(),
                    consoleMessage.message()
                );
                
                switch (consoleMessage.messageLevel()) {
                    case ERROR:
                        Log.e("BrowserConsole", message);
                        break;
                    case WARNING:
                        Log.w("BrowserConsole", message);
                        break;
                    case DEBUG:
                        Log.d("BrowserConsole", message);
                        break;
                    default:
                        Log.i("BrowserConsole", message);
                        break;
                }
                return true;
            }

            // ===== GEOLOCATION =====
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, 
                    GeolocationPermissions.Callback callback) {
                Log.d(TAG, "Geolocation request from: " + origin);
                callback.invoke(origin, true, false);
            }

            // ===== PERMISSION REQUESTS (Camera, Mic, etc.) =====
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                Log.d(TAG, "Permission request: " + request.getOrigin());
                for (String resource : request.getResources()) {
                    Log.d(TAG, "  - " + resource);
                }
                request.grant(request.getResources());
            }

            // ===== FILE CHOOSER =====
            @Override
            public boolean onShowFileChooser(WebView webView, 
                    ValueCallback<String[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                Log.d(TAG, "File chooser requested");
                // Handle file upload - delegate to Activity
                return false;
            }

            // ===== FULLSCREEN VIDEO =====
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                Log.d(TAG, "Fullscreen video requested");
                // Delegate to Activity for fullscreen handling
            }

            @Override
            public void onHideCustomView() {
                Log.d(TAG, "Fullscreen video hidden");
                // Delegate to Activity
            }

            // ===== JAVASCRIPT ALERTS/DIALOGS =====
            @Override
            public boolean onJsAlert(WebView view, String url, String message, 
                    android.webkit.JsResult result) {
                Log.d(TAG, "JS Alert: " + message);
                return false;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, 
                    android.webkit.JsResult result) {
                Log.d(TAG, "JS Confirm: " + message);
                return false;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, 
                    String defaultValue, android.webkit.JsPromptResult result) {
                Log.d(TAG, "JS Prompt: " + message);
                return false;
            }
        });
    }

    // ===== YOUTUBE LINK EXTRACTION =====
    private void extractYouTubeLinks() {
        String script = 
            "(function() {" +
            "  var links = [];" +
            "  var patterns = [" +
            "    /https?:\\/\\/(?:www\\.)?youtube\\.com\\/embed\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
            "    /https?:\\/\\/(?:www\\.)?youtube\\.com\\/watch\\?v=[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
            "    /https?:\\/\\/youtu\\.be\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
            "    /https?:\\/\\/(?:www\\.)?youtube\\.com\\/v\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
            "    /https?:\\/\\/www\\.youtube-nocookie\\.com\\/embed\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
            "    /https?:\\/\\/m\\.youtube\\.com\\/watch\\?v=[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
            "    /youtube\\.com\\/shorts\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g" +
            "  ];" +
            "  var html = document.documentElement.innerHTML;" +
            "  patterns.forEach(function(pattern) {" +
            "    var matches = html.match(pattern);" +
            "    if (matches) {" +
            "      matches.forEach(function(link) {" +
            "        link = link.replace(/[\"'<]/g, '').split('&')[0].split('?')[0].replace(/\\/$/, '');" +
            "        if (link.includes('youtube.com') || link.includes('youtu.be')) {" +
            "          links.push(link);" +
            "        }" +
            "      });" +
            "    }" +
            "  });" +
            "  document.querySelectorAll('iframe, video, source, embed, object').forEach(function(el) {" +
            "    var src = el.src || el.getAttribute('src') || el.getAttribute('data-src') || el.getAttribute('data');" +
            "    if (src && (src.includes('youtube.com') || src.includes('youtu.be'))) {" +
            "      links.push(src);" +
            "    }" +
            "  });" +
            "  document.querySelectorAll('a').forEach(function(el) {" +
            "    var href = el.href;" +
            "    if (href && (href.includes('youtube.com') || href.includes('youtu.be'))) {" +
            "      links.push(href);" +
            "    }" +
            "  });" +
            "  return JSON.stringify([...new Set(links)]);" +
            "})()";

        evaluateJavascript(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                youtubeLinks.clear();
                if (value != null && !value.equals("null")) {
                    try {
                        // Parse JSON array
                        String cleaned = value.substring(1, value.length() - 1);
                        String[] items = cleaned.split("\",\"");
                        for (String item : items) {
                            String link = item.replace("\"", "").trim();
                            if (!link.isEmpty() && !youtubeLinks.contains(link)) {
                                youtubeLinks.add(link);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing YouTube links: " + e.getMessage());
                    }
                }

                Log.d(TAG, "Found " + youtubeLinks.size() + " YouTube links");
                
                if (youtubeLinksListener != null) {
                    youtubeLinksListener.onLinksFound(new ArrayList<>(youtubeLinks));
                }
            }
        });
    }

    // ===== EXTENSION SCRIPT INJECTION =====
    private void injectExtensionScripts() {
        for (String script : extensionScripts) {
            evaluateJavascript(script, null);
        }
    }

    // ===== DARK MODE =====
    private void applyDarkMode() {
        String darkModeScript = 
            "(function() {" +
            "  if (!document.getElementById('browser-dark-mode-style')) {" +
            "    var style = document.createElement('style');" +
            "    style.id = 'browser-dark-mode-style';" +
            "    style.textContent = 'html { filter: invert(88%) hue-rotate(180deg) !important; } img, video, canvas { filter: invert(100%) hue-rotate(180deg) !important; }';" +
            "    document.head.appendChild(style);" +
            "  }" +
            "})()";
        evaluateJavascript(darkModeScript, null);
    }

    // ===== AD BLOCKING =====
    private boolean isAdDomain(String url) {
        String[] adDomains = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "ads.yahoo.com", "adnxs.com", "criteo.com", "outbrain.com"
        };
        for (String domain : adDomains) {
            if (url.contains(domain)) return true;
        }
        return false;
    }

    private boolean isTrackerDomain(String url) {
        String[] trackerDomains = {
            "google-analytics.com", "facebook.com/tr", "hotjar.com",
            "mixpanel.com", "segment.com", "amplitude.com"
        };
        for (String domain : trackerDomains) {
            if (url.contains(domain)) return true;
        }
        return false;
    }

    // ===== EXTENSION URL HANDLING =====
    private boolean handleExtensionUrl(String url) {
        // Check if any extension wants to handle this URL
        // Return true if handled, false otherwise
        return false;
    }

    // ===== PUBLIC METHODS =====

    public void setOnPageLoadListener(OnPageLoadListener listener) {
        this.pageLoadListener = listener;
    }

    public void setOnYouTubeLinksFoundListener(OnYouTubeLinksFoundListener listener) {
        this.youtubeLinksListener = listener;
    }

    public void setOnExtensionMessageListener(OnExtensionMessageListener listener) {
        this.extensionMessageListener = listener;
    }

    public void addExtensionScript(String script) {
        extensionScripts.add(script);
        if (isPageLoaded) {
            evaluateJavascript(script, null);
        }
    }

    public void removeExtensionScript(String script) {
        extensionScripts.remove(script);
    }

    public void clearExtensionScripts() {
        extensionScripts.clear();
    }

    public void setAdBlockEnabled(boolean enabled) {
        this.adBlockEnabled = enabled;
    }

    public void setDarkModeEnabled(boolean enabled) {
        this.darkModeEnabled = enabled;
        if (enabled && isPageLoaded) {
            applyDarkMode();
        }
    }

    public List<String> getYoutubeLinks() {
        return new ArrayList<>(youtubeLinks);
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public int getLoadProgress() {
        return loadProgress;
    }

    public boolean isPageLoaded() {
        return isPageLoaded;
    }

    public void forceYouTubeExtraction() {
        extractYouTubeLinks();
    }

    public void executeCustomScript(String script) {
        evaluateJavascript(script, null);
    }

    public void executeCustomScriptWithCallback(String script, ValueCallback<String> callback) {
        evaluateJavascript(script, callback);
    }

    // ===== JAVASCRIPT INTERFACES =====

    @SuppressLint("JavascriptInterface")
    private class BrowserJSInterface {

        @JavascriptInterface
        public void sendMessage(String extensionId, String message) {
            Log.d(TAG, "Extension message from " + extensionId + ": " + message);
            if (extensionMessageListener != null) {
                extensionMessageListener.onMessage(extensionId, message);
            }
        }

        @JavascriptInterface
        public void showNotification(String message) {
            Log.d(TAG, "Notification: " + message);
            post(() -> {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public String getUserAgent() {
            return getSettings().getUserAgentString();
        }

        @JavascriptInterface
        public void setUserAgent(String ua) {
            post(() -> {
                getSettings().setUserAgentString(ua);
                reload();
            });
        }

        @JavascriptInterface
        public void clearCache() {
            post(() -> {
                clearCache(true);
                clearHistory();
                CookieManager.getInstance().removeAllCookies(null);
            });
        }

        @JavascriptInterface
        public String getPageHtml() {
            // This is a simplified version - in production, use proper async callback
            return "HTML extraction requires async callback";
        }
    }

    @SuppressLint("JavascriptInterface")
    private class YouTubeExtractorJS {

        @JavascriptInterface
        public String extractLinks() {
            // Synchronous extraction - returns JSON string
            // Note: This is limited, prefer async version
            return "[]";
        }

        @JavascriptInterface
        public void extractAndNotify() {
            post(() -> {
                extractYouTubeLinks();
            });
        }

        @JavascriptInterface
        public int getLinkCount() {
            return youtubeLinks.size();
        }
    }

    // ===== TOUCH HANDLING =====
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    // ===== CLEANUP =====
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearExtensionScripts();
        youtubeLinks.clear();
        Log.d(TAG, "BrowserWebView detached and cleaned up");
    }

    // ===== UTILITY METHODS =====

    public void loadUrlWithHeaders(String url, Map<String, String> headers) {
        loadUrl(url, headers);
    }

    public void loadDataWithBaseUrl(String baseUrl, String data, String mimeType, 
            String encoding, String historyUrl) {
        loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }

    public void zoomIn() {
        zoomIn();
    }

    public void zoomOut() {
        zoomOut();
    }

    public void resetZoom() {
        // Reset zoom to 100%
        evaluateJavascript("document.body.style.zoom = '1';", null);
    }

    public void scrollToTop() {
        evaluateJavascript("window.scrollTo(0, 0);", null);
    }

    public void scrollToBottom() {
        evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null);
    }

    public void findOnPage(String query) {
        findAllAsync(query);
    }

    public void clearFindMatches() {
        clearMatches();
    }

    // ===== SCREENSHOT (for thumbnails) =====
    public Bitmap captureScreenshot() {
        // Enable drawing cache
        setDrawingCacheEnabled(true);
        setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        buildDrawingCache();
        Bitmap bitmap = getDrawingCache();
        if (bitmap != null) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        setDrawingCacheEnabled(false);
        return bitmap;
    }

    // ===== PERFORMANCE METRICS =====
    public void getPerformanceMetrics(ValueCallback<String> callback) {
        String script = 
            "(function() {" +
            "  var timing = performance.timing;" +
            "  return JSON.stringify({" +
            "    'domContentLoaded': timing.domContentLoadedEventEnd - timing.navigationStart," +
            "    'loadComplete': timing.loadEventEnd - timing.navigationStart," +
            "    'dns': timing.domainLookupEnd - timing.domainLookupStart," +
            "    'tcp': timing.connectEnd - timing.connectStart," +
            "    'ssl': timing.connectEnd - timing.secureConnectionStart," +
            "    'ttfb': timing.responseStart - timing.navigationStart," +
            "    'domProcessing': timing.domComplete - timing.domLoading" +
            "  });" +
            "})()";
        evaluateJavascript(script, callback);
    }
}
