package com.shanglin.deepseekapp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek App 1:1 复刻 —— WebView 套壳。
 * 官方开源 chat-demo 静态站点放在 assets/site/，通过虚拟 HTTPS 域名
 * https://appassets.androidplatform.net/ 提供，保证 localStorage/IndexedDB/
 * getUserMedia 等安全上下文能力可用；所有 API 请求直连官方 api.deepseek.com。
 */
public class MainActivity extends Activity {

    private static final String TAG = "DSApp";
    public static final String HOST = "appassets.androidplatform.net";
    public static final String HOME_URL = "https://" + HOST + "/index.html";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 1001;
    private static final int REQ_AUDIO = 1002;

    /** blob 下载桥接：拦截 a[download] 的点击，把 blob 转 base64 交给 Java 保存到系统下载目录 */
    private static final String BLOB_HOOK =
        "(function(){"
        + "if(window.__dsBlobHook)return;window.__dsBlobHook=true;"
        + "document.addEventListener('click',function(e){"
        + "var t=e.target;var a=t&&t.closest?t.closest('a[download]'):null;"
        + "if(!a||!a.href||a.href.indexOf('blob:')!==0)return;"
        + "e.preventDefault();e.stopPropagation();"
        + "var name=a.getAttribute('download')||'download';"
        + "fetch(a.href).then(function(r){return r.blob();}).then(function(b){"
        + "var fr=new FileReader();"
        + "fr.onload=function(){var s=String(fr.result);window.DeepSeekNative.saveBlob(name,s.substring(s.indexOf(',')+1));};"
        + "fr.readAsDataURL(b);"
        + "}).catch(function(err){console.warn('blob save failed',err);});"
        + "},true);"
        + "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                CrashLog.record(MainActivity.this, e);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webView);
        setContentView(root);

        setupWebView();
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }
        webView.loadUrl(HOME_URL);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage：保存 API Key / 设置
        s.setDatabaseEnabled(true);            // IndexedDB：保存对话与附件
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF); // demo 自带明暗主题，避免系统强制反色
        }
        webView.addJavascriptInterface(new JsBridge(), "DeepSeekNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                return serveAsset(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (scheme == null) return false;
                if (HOST.equals(uri.getHost()) && ("https".equals(scheme) || "http".equals(scheme))) {
                    return false; // 站内资源/页面，交给本地托管
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(BLOB_HOOK, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    cb.onReceiveValue(null);
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                String[] resources = request.getResources();
                boolean needAudio = false;
                for (String r : resources) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) needAudio = true;
                }
                if (needAudio && Build.VERSION.SDK_INT >= 23
                        && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                    request.deny();
                    Toast.makeText(MainActivity.this, "请授予麦克风权限以使用语音输入", Toast.LENGTH_SHORT).show();
                    return;
                }
                request.grant(resources);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin,
                    final android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, cm.message() + " (" + cm.lineNumber() + ")");
                return true;
            }
        });
    }

    /** 把虚拟域名下的请求映射到 assets/site/ */
    private WebResourceResponse serveAsset(Uri uri) {
        if (uri == null || !HOST.equals(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
        String assetPath = "site" + path; // site/index.html, site/static/js/xxx.js ...
        try {
            InputStream is = getAssets().open(assetPath);
            String mime = mimeFor(assetPath);
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "max-age=3600");
            String encoding = (mime.startsWith("text/") || mime.contains("javascript")
                    || mime.contains("json") || mime.contains("xml")) ? "utf-8" : null;
            return new WebResourceResponse(mime, encoding, 200, "OK", headers, is);
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                    new HashMap<String, String>(),
                    new ByteArrayInputStream("Not Found".getBytes()));
        }
    }

    private static String mimeFor(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json") || p.endsWith(".map")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".txt") || p.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String clip = data.getDataString();
                android.content.ClipData clipData = data.getClipData();
                if (clipData != null && clipData.getItemCount() > 0) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri u = clipData.getItemAt(i).getUri();
                        try {
                            getContentResolver().takePersistableUriPermission(u,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) { }
                        results[i] = u;
                    }
                } else if (clip != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    public class JsBridge {
        @JavascriptInterface
        public void saveBlob(String name, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String fileName = sanitize(name == null ? "download" : name);
                boolean ok;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/DeepSeek");
                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new IOException("insert failed");
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(bytes);
                    os.flush();
                    os.close();
                    ok = true;
                } else {
                    File dir = new File(Environment.getExternalStorageDirectory(),
                            Environment.DIRECTORY_DOWNLOADS + "/DeepSeek");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir failed");
                    FileOutputStream fos = new FileOutputStream(new File(dir, fileName));
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                    ok = true;
                }
                final boolean saved = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this,
                                saved ? "已保存到 下载/DeepSeek/" : "保存失败",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (final Exception e) {
                Log.e(TAG, "saveBlob failed", e);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this, "保存失败：" + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        @JavascriptInterface
        public String version() {
            return "1.0.0";
        }
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (s.isEmpty()) s = "download";
        return s;
    }
}
