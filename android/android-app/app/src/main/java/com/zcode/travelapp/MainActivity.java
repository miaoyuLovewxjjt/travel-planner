package com.zcode.travelapp;

import androidx.activity.ComponentActivity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 我的行程 - WebView 壳
 * 加载本地 assets/index.html（应用本体），数据保存在 WebView localStorage。
 * 支持：JSON 导出下载（保存到「下载」目录）、JSON 导入（系统文件选择器）。
 */
public class MainActivity extends ComponentActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback != null) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        filePathCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                    } else {
                        filePathCallback.onReceiveValue(null);
                    }
                    filePathCallback = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                       // localStorage
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 外部链接（http/https 非本应用）交给系统浏览器
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                return false;
            }
        });

        // 文件选择（导入 JSON）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePath,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePath;
                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // 下载（导出 JSON → 保存到「下载」目录）
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                String fileName = "行程数据.json";
                if (contentDisposition != null && contentDisposition.contains("filename=")) {
                    String fn = contentDisposition.replaceAll(".*filename=\"?([^\";]+)\"?.*", "$1");
                    if (!fn.isEmpty() && !fn.equals(contentDisposition)) fileName = fn;
                }
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(10000);
                InputStream in = conn.getInputStream();
                byte[] data = new byte[8192];
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = in.read(data)) != -1) bos.write(data, 0, n);
                in.close();
                conn.disconnect();

                Uri uri = saveToDownloads(fileName, "application/json", bos.toByteArray());
                Toast.makeText(this, "已导出：" + fileName, Toast.LENGTH_LONG).show();
                if (uri != null) {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/json");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    startActivity(Intent.createChooser(share, "分享数据文件"));
                }
            } catch (Exception e) {
                Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // 数据位置桥接（供页面「数据管理」面板显示真实存储路径）
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public String getDevice() {
                return "phone";
            }
            @android.webkit.JavascriptInterface
            public String getPackageName() {
                return MainActivity.this.getPackageName();
            }
            @android.webkit.JavascriptInterface
            public String getAppDir() {
                return getFilesDir().getAbsolutePath();
            }
            @android.webkit.JavascriptInterface
            public String getWebViewDir() {
                // WebView 数据目录（localStorage 实际存储位置）
                File dir = getDir("webview", 0);
                return dir != null ? dir.getAbsolutePath() : "";
            }
            @android.webkit.JavascriptInterface
            public String getDownloadDir() {
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            }
            @android.webkit.JavascriptInterface
            public void scheduleAllReminders(String json) {
                ReminderHelper.scheduleAll(MainActivity.this, json);
            }
            @android.webkit.JavascriptInterface
            public void requestNotifyPermission() {
                ReminderHelper.ensureNotificationChannel(MainActivity.this);
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
                }
            }
            @android.webkit.JavascriptInterface
            public String getNotifyPermission() {
                if (Build.VERSION.SDK_INT >= 33) {
                    return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                            == android.content.pm.PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
                }
                return "granted"; // Android 13 以下无需通知权限
            }
        }, "AndroidBridge");

        webView.loadUrl("file:///android_asset/index.html");
    }

    /** 保存文件到「下载」目录（Android 10+ 用 MediaStore，旧版用公共目录） */
    private Uri saveToDownloads(String fileName, String mime, byte[] bytes) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    getContentResolver().openOutputStream(uri).write(bytes);
                    return uri;
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(bytes);
                }
                return Uri.fromFile(f);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        // 应用内返回：不触发 WebView 历史（hash 路由由页面处理），直接退出
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
