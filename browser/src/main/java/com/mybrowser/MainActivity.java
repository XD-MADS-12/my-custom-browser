package com.mybrowser;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private BrowserWebView webView;
    private ProgressBar progressBar;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ExtensionManager extensionManager;
    private YouTubeExtractor youtubeExtractor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Initialize components
        extensionManager = new ExtensionManager(this);
        youtubeExtractor = new YouTubeExtractor();

        // Create main layout
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            8
        ));
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        mainLayout.addView(progressBar);

        // WebView container
        FrameLayout webViewContainer = new FrameLayout(this);
        webViewContainer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ));

        // Create BrowserWebView
        webView = new BrowserWebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        webViewContainer.addView(webView);
        mainLayout.addView(webViewContainer);
        setContentView(mainLayout);

        // Setup WebView listeners
        setupWebView();

        // Load extensions
        if (BuildConfig.ENABLE_EXTENSIONS) {
            extensionManager.loadBundledExtensions();
        }

        // Load home page
        webView.loadUrl("https://www.google.com");
    }

    private void setupWebView() {
        // Page load listener
        webView.setOnPageLoadListener(new BrowserWebView.OnPageLoadListener() {
            @Override
            public void onPageStarted(String url) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                setTitle("Loading...");
            }

            @Override
            public void onPageFinished(String url) {
                progressBar.setVisibility(View.GONE);
                setTitle(webView.getCurrentTitle());
                
                // Inject extension scripts
                if (BuildConfig.ENABLE_EXTENSIONS) {
                    extensionManager.injectScripts(webView, url);
                }
            }

            @Override
            public void onProgressChanged(int progress) {
                progressBar.setProgress(progress);
                if (progress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedTitle(String title) {
                setTitle(title);
            }

            @Override
            public void onReceivedError(String error) {
                Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        // YouTube links listener
        webView.setOnYouTubeLinksFoundListener(new BrowserWebView.OnYouTubeLinksFoundListener() {
            @Override
            public void onLinksFound(List<String> links) {
                if (!links.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                            " " + links.size() + " YouTube links found!", 
                            Toast.LENGTH_LONG).show();
                        
                        // Show links
                        StringBuilder sb = new StringBuilder("YouTube Links Found:\n\n");
                        for (int i = 0; i < links.size(); i++) {
                            sb.append((i + 1) + ". " + links.get(i) + "\n");
                        }
                        
                        // You can show this in a dialog or save to file
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("YouTube Links (" + links.size() + ")");
                        builder.setMessage(sb.toString());
                        builder.setPositiveButton("Copy All", (dialog, which) -> {
                            android.content.ClipboardManager clipboard = 
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            android.content.ClipData clip = 
                                android.content.ClipData.newPlainText("YouTube Links", sb.toString());
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(MainActivity.this, "Copied!", Toast.LENGTH_SHORT).show();
                        });
                        builder.setNegativeButton("Close", null);
                        builder.show();
                    });
                }
            }
        });
    }

    // Back button
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.setWebChromeClient(new WebChromeClient());
                customView.setVisibility(View.GONE);
                ((ViewGroup) getWindow().getDecorView()).removeView(customView);
                customView = null;
                customViewCallback.onCustomViewHidden();
                return true;
            }
            
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
}
