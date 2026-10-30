package com.tfnews.studio;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
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

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQ_FILE = 11;
    private static final int REQ_MIC = 12;
    private static final int REQ_BROWSE = 13;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;

    private File tmpFile;
    private FileOutputStream tmpOut;
    private String tmpName = "file";
    private String tmpMime = "application/octet-stream";
    private Uri lastUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    toast("লিংক খোলা যায়নি");
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsMic = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                    }
                    if (!wantsMic) {
                        request.deny();
                        return;
                    }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingPermission = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                ArrayList<String> types = new ArrayList<>();
                boolean any = false;
                String[] accept = params.getAcceptTypes();
                if (accept != null) {
                    for (String a : accept) {
                        if (a == null) continue;
                        for (String t : a.split(",")) {
                            t = t.trim();
                            if (t.isEmpty()) continue;
                            if (t.startsWith(".")) {
                                any = true;
                                continue;
                            }
                            types.add(t);
                        }
                    }
                }
                if (any || types.isEmpty()) {
                    i.setType("*/*");
                } else if (types.size() == 1) {
                    i.setType(types.get(0));
                } else {
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                }
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(Intent.createChooser(i, "ফাইল বেছে নিন"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
        }

        // auto-update: background check every 6 hours + a check now
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 14);
        }
        Updater.schedule(this);
        boolean fromNotif = getIntent() != null && getIntent().getBooleanExtra("doUpdate", false);
        web.postDelayed(() -> Updater.checkInApp(MainActivity.this, false, fromNotif), 2500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("doUpdate", false)) {
            Updater.checkInApp(this, false, true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.resumePending(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BROWSE) {
            if (resultCode == RESULT_OK && data != null && data.getStringExtra("page") != null) {
                final String page = data.getStringExtra("page");
                web.evaluateJavascript("window.tfPickedNews && window.tfPickedNews(" + page + ")", null);
            }
            return;
        }
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int k = 0; k < n; k++) result[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && pendingPermission != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingPermission.deny();
                toast("মাইক্রোফোনের অনুমতি দেওয়া হয়নি");
            }
            pendingPermission = null;
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.tfBack && window.tfBack()) ? '1' : '0'", value -> {
            if (!"\"1\"".equals(value)) {
                finish();
            }
        });
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    private Uri publishFile(File f, String name, String mime) throws Exception {
        boolean video = mime.startsWith("video/");
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS) + "/TF News Studio");
        Uri collection = video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = getContentResolver().insert(collection, cv);
        if (uri == null) return null;
        try (OutputStream out = getContentResolver().openOutputStream(uri);
             InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return uri;
    }

    private class Bridge {
        @JavascriptInterface
        public boolean saveBegin(String name, String mime) {
            try {
                tmpName = name;
                tmpMime = mime;
                tmpFile = new File(getCacheDir(), "export.tmp");
                tmpOut = new FileOutputStream(tmpFile, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean saveChunk(String b64) {
            try {
                tmpOut.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String saveEnd() {
            try {
                tmpOut.close();
                lastUri = publishFile(tmpFile, tmpName, tmpMime);
                tmpFile.delete();
                return lastUri != null ? "ok" : "fail";
            } catch (Exception e) {
                return "fail: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void share() {
            runOnUiThread(() -> {
                if (lastUri == null) return;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(tmpMime);
                i.putExtra(Intent.EXTRA_STREAM, lastUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(i, "শেয়ার করুন"));
                } catch (Exception e) {
                    toast("শেয়ার করা যায়নি");
                }
            });
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void immersive(boolean on) {
            runOnUiThread(() -> {
                View d = getWindow().getDecorView();
                if (on) {
                    d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                } else {
                    d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            });
        }

        @JavascriptInterface
        public void openBrowser(final String url, final boolean adblock) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, BrowserActivity.class);
                i.putExtra("url", url);
                i.putExtra("adblock", adblock);
                startActivityForResult(i, REQ_BROWSE);
            });
        }

        /** Async GET without CORS limits. Result: window.tfHttpCb(id, ok, data). b64=true returns base64 bytes. */
        @JavascriptInterface
        public void httpGet(final String id, final String url, final boolean b64) {
            new Thread(() -> {
                String out;
                boolean ok = false;
                try {
                    String u = url;
                    HttpURLConnection c = null;
                    for (int hop = 0; hop < 6; hop++) {
                        c = (HttpURLConnection) new URL(u).openConnection();
                        c.setInstanceFollowRedirects(false);
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(25000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                        c.setRequestProperty("Accept-Language", "bn-BD,bn;q=0.9,en;q=0.8");
                        int code = c.getResponseCode();
                        if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                            u = new URL(new URL(u), c.getHeaderField("Location")).toString();
                            c.disconnect();
                            continue;
                        }
                        break;
                    }
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            bo.write(buf, 0, n);
                            if (bo.size() > 12 * 1024 * 1024) break;
                        }
                        in.close();
                    }
                    ok = code < 400;
                    if (b64) {
                        String ct = c.getContentType();
                        out = (ct == null ? "" : ct) + "|" + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    } else {
                        out = bo.toString("UTF-8");
                    }
                    c.disconnect();
                } catch (Exception e) {
                    out = String.valueOf(e.getMessage());
                }
                final String js = "window.tfHttpCb && window.tfHttpCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(out) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        @JavascriptInterface
        public void checkUpdate() {
            Updater.checkInApp(MainActivity.this, true, false);
        }

        @JavascriptInterface
        public String appVersion() {
            return Updater.currentName(MainActivity.this);
        }

        @JavascriptInterface
        public void toast(String m) {
            MainActivity.this.toast(m);
        }
    }
}
