package com.codex.women;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private static final String APP_URL = "file:///android_asset/index.html";

    private FrameLayout rootView;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBarTheme(false);

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(Color.rgb(248, 243, 239));
        try {
            webView = new WebView(this);
        } catch (Throwable error) {
            showStartupError();
            return;
        }
        webView.setBackgroundColor(Color.rgb(248, 243, 239));
        rootView.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootView);
        applySystemBarInsets();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
            ) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "未找到可用的文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void applySystemBarInsets() {
        rootView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        rootView.requestApplyInsets();
    }

    private void applySystemBarTheme(boolean darkMode) {
        int color = darkMode ? Color.rgb(23, 19, 22) : Color.rgb(248, 243, 239);
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags = darkMode ? (flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            : (flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        flags = darkMode ? (flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            : (flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setSystemUiVisibility(flags);

        if (rootView != null) rootView.setBackgroundColor(color);
        if (webView != null) webView.setBackgroundColor(color);
    }

    private void showStartupError() {
        TextView message = new TextView(this);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(Color.rgb(45, 37, 42));
        message.setTextSize(16);
        message.setPadding(48, 48, 48, 48);
        message.setText("应用无法加载系统网页组件。\n请更新 Android System WebView 或 Chrome 后重试。");
        rootView.addView(message, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootView);
        applySystemBarInsets();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) {
            return;
        }
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
            "window.handleAndroidBack ? window.handleAndroidBack() : false",
            value -> {
                if (!"true".equals(value)) {
                    MainActivity.super.onBackPressed();
                }
            }
        );
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        fileExecutor.shutdown();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
        }
        super.onDestroy();
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public String getData(String key) {
            File target = storageFile(key);
            if (target == null || !target.isFile()) {
                return null;
            }
            try {
                byte[] bytes = Files.readAllBytes(target.toPath());
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception error) {
                return null;
            }
        }

        @JavascriptInterface
        public boolean putData(String key, String value) {
            File target = storageFile(key);
            if (target == null || value == null) {
                return false;
            }
            File temp = new File(target.getParentFile(), target.getName() + ".tmp");
            try (FileOutputStream stream = new FileOutputStream(temp)) {
                stream.write(value.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            } catch (Exception error) {
                temp.delete();
                return false;
            }
            if (!temp.renameTo(target)) {
                temp.delete();
                return false;
            }
            return true;
        }

        @JavascriptInterface
        public void removeData(String key) {
            File target = storageFile(key);
            if (target != null && target.isFile()) {
                target.delete();
            }
        }

        private File storageFile(String key) {
            if (key == null) {
                return null;
            }
            String safeKey = key.trim().replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeKey.isEmpty() || safeKey.length() > 100) {
                return null;
            }
            File root = new File(getFilesDir(), "webstore");
            if (!root.exists() && !root.mkdirs()) {
                return null;
            }
            return new File(root, safeKey + ".json");
        }

        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Icepear message", text == null ? "" : text));
                }
            });
        }

        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(share, "转发消息"));
            });
        }

        @JavascriptInterface
        public void saveFile(String requestedName, String base64Data, String mimeType) {
            final String safeName = sanitizeName(requestedName);
            final String safeMime = mimeType == null || mimeType.isBlank()
                ? "application/octet-stream"
                : mimeType;
            fileExecutor.execute(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    String location = writeDownload(safeName, safeMime, bytes);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "已保存到 " + location, Toast.LENGTH_LONG).show();
                        webView.evaluateJavascript(
                            "window.notifyNativeSave && window.notifyNativeSave(true)",
                            null
                        );
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "保存失败，请重试", Toast.LENGTH_LONG).show();
                        webView.evaluateJavascript(
                            "window.notifyNativeSave && window.notifyNativeSave(false)",
                            null
                        );
                    });
                }
            });
        }

        @JavascriptInterface
        public void setSystemBars(boolean darkMode) {
            runOnUiThread(() -> applySystemBarTheme(darkMode));
        }

        private String writeDownload(String name, String mimeType, byte[] bytes) throws Exception {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Icepear");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new IllegalStateException("Cannot create download");
                }
                try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                    if (stream == null) {
                        throw new IllegalStateException("Cannot open download");
                    }
                    stream.write(bytes);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                return "下载/Icepear/" + name;
            }

            File root = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Icepear");
            if (!root.exists() && !root.mkdirs()) {
                throw new IllegalStateException("Cannot create app download folder");
            }
            File target = new File(root, name);
            try (FileOutputStream stream = new FileOutputStream(target)) {
                stream.write(bytes);
            }
            return target.getAbsolutePath();
        }

        private String sanitizeName(String value) {
            String name = value == null ? "导出文件" : value.trim();
            name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
            if (name.isEmpty()) {
                name = "导出文件";
            }
            return name.length() > 100 ? name.substring(0, 100) : name;
        }
    }
}
