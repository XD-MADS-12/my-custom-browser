package com.mybrowser;

import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

public class YouTubeExtractor {
    private static final String TAG = "YouTubeExtractor";
    private List<String> extractedLinks;
    private OnLinksExtractedListener listener;

    public interface OnLinksExtractedListener {
        void onLinksExtracted(List<String> links);
    }

    public YouTubeExtractor() {
        this.extractedLinks = new ArrayList<>();
    }

    public void setOnLinksExtractedListener(OnLinksExtractedListener listener) {
        this.listener = listener;
    }

    public void extractFromWebView(WebView webView) {
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
            "    if (matches) links = links.concat(matches);" +
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

        webView.evaluateJavascript(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                extractedLinks.clear();
                if (value != null && !value.equals("null")) {
                    try {
                        // Remove brackets
                        String cleaned = value.substring(1, value.length() - 1);
                        if (!cleaned.isEmpty()) {
                            String[] items = cleaned.split("\",\"");
                            for (String item : items) {
                                String link = item.replace("\"", "").trim();
                                if (!link.isEmpty() && !extractedLinks.contains(link)) {
                                    extractedLinks.add(link);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing links: " + e.getMessage());
                    }
                }

                Log.d(TAG, "Extracted " + extractedLinks.size() + " YouTube links");
                
                if (listener != null) {
                    listener.onLinksExtracted(new ArrayList<>(extractedLinks));
                }
            }
        });
    }

    public List<String> getExtractedLinks() {
        return extractedLinks;
    }

    public void clearLinks() {
        extractedLinks.clear();
    }
}
