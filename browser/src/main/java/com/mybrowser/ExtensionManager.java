package com.mybrowser;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExtensionManager {
    private static final String TAG = "ExtensionManager";
    private Context context;
    private List<Extension> loadedExtensions;
    private List<String> extensionScripts;

    public ExtensionManager(Context context) {
        this.context = context;
        this.loadedExtensions = new ArrayList<>();
        this.extensionScripts = new ArrayList<>();
    }

    public void loadBundledExtensions() {
        Log.d(TAG, "Loading bundled extensions...");
        
        try {
            // Load YouTube Extractor extension
            InputStream is = context.getAssets().open("extensions/youtube-extractor/manifest.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            
            String json = new String(buffer, "UTF-8");
            JSONObject manifest = new JSONObject(json);
            
            Extension ext = new Extension();
            ext.name = manifest.optString("name", "YouTube Extractor");
            ext.id = manifest.optString("id", "youtube-extractor");
            ext.version = manifest.optString("version", "1.0");
            ext.enabled = true;
            
            loadedExtensions.add(ext);
            Log.d(TAG, "Loaded extension: " + ext.name);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading extension: " + e.getMessage());
        }
    }

    public void injectScripts(WebView webView, String url) {
        for (Extension ext : loadedExtensions) {
            if (ext.enabled) {
                String script = getExtensionScript(ext.id, url);
                if (script != null) {
                    webView.evaluateJavascript(script, null);
                }
            }
        }
    }

    private String getExtensionScript(String extensionId, String url) {
        if ("youtube-extractor".equals(extensionId)) {
            return "(function() {" +
                "  console.log('[YouTube Extractor] Active on: ' + window.location.href);" +
                "  window.BrowserYouTubeExtractor = {" +
                "    extract: function() {" +
                "      var links = [];" +
                "      var patterns = [" +
                "        /https?:\\/\\/(?:www\\.)?youtube\\.com\\/embed\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
                "        /https?:\\/\\/(?:www\\.)?youtube\\.com\\/watch\\?v=[a-zA-Z0-9_-]+[^\\s\"'<>]*/g," +
                "        /https?:\\/\\/youtu\\.be\\/[a-zA-Z0-9_-]+[^\\s\"'<>]*/g" +
                "      ];" +
                "      var html = document.documentElement.innerHTML;" +
                "      patterns.forEach(function(pattern) {" +
                "        var matches = html.match(pattern);" +
                "        if (matches) links = links.concat(matches);" +
                "      });" +
                "      return [...new Set(links)];" +
                "    }" +
                "  };" +
                "  console.log('[YouTube Extractor] Ready! Use BrowserYouTubeExtractor.extract()');" +
                "})()";
        }
        return null;
    }

    public boolean shouldInterceptUrl(String url) {
        return false;
    }

    public void installFromJson(String json) {
        try {
            JSONObject extData = new JSONObject(json);
            Extension ext = new Extension();
            ext.name = extData.optString("name");
            ext.id = extData.optString("id");
            ext.version = extData.optString("version", "1.0");
            ext.enabled = true;
            loadedExtensions.add(ext);
            Log.d(TAG, "Installed extension: " + ext.name);
        } catch (Exception e) {
            Log.e(TAG, "Error installing extension: " + e.getMessage());
        }
    }

    public List<Extension> getLoadedExtensions() {
        return loadedExtensions;
    }

    public void enableExtension(String id) {
        for (Extension ext : loadedExtensions) {
            if (ext.id.equals(id)) {
                ext.enabled = true;
                break;
            }
        }
    }

    public void disableExtension(String id) {
        for (Extension ext : loadedExtensions) {
            if (ext.id.equals(id)) {
                ext.enabled = false;
                break;
            }
        }
    }

    private static class Extension {
        String name;
        String id;
        String version;
        boolean enabled;
    }
}
