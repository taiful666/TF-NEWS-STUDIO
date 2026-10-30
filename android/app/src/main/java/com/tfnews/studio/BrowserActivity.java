package com.tfnews.studio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** In-app newspaper browser with an on/off ad blocker and a "take this news" button. */
public class BrowserActivity extends Activity {

    static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "googletagservices.com",
            "amazon-adsystem.com", "adnxs.com", "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net",
            "smartadserver.com", "casalemedia.com", "yieldmo.com", "teads.tv", "adform.net", "media.net",
            "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "adskeeper.com", "adskeeper.co.uk", "zergnet.com",
            "popads.net", "popcash.net", "propellerads.com", "onclickads.net", "onclkds.com", "adsterra.com", "adsterratech.com",
            "exoclick.com", "exdynsrv.com", "juicyads.com", "hilltopads.net", "clickadu.com", "a-ads.com", "adcash.com",
            "admaven.com", "ad-maven.com", "richpush.co", "pushame.com", "evadav.com", "monetag.com", "galaksion.com"
    };
    static final String AD_CSS = "ins.adsbygoogle,.adsbygoogle,[id^=div-gpt-ad],[id^=google_ads_iframe],[id*=google_ads],iframe[id^=aswift_],"
            + "[data-ad-slot],[data-ad-unit],[data-google-query-id],amp-ad,amp-embed,.gpt-ad,.dfp-ad,[class*=dfp-ad],"
            + "[id^=taboola-],.trc_related_container,[class*=taboola],.OUTBRAIN,[class^=mgid],[id^=mgid],[id*=ScriptRootC],"
            + ".ad-slot,.ad-container,.ad-wrapper,.ads-wrapper,.ad-unit,.advertisement,.adsense,.sticky-ad,.stickyAd";

    private WebView wv;
    private boolean adblock;
    private TextView titleView;
    private Button adButton;
    private ProgressBar bar;
    private String pickScript = "";

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    static boolean isAd(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        for (String d : AD_HOSTS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adblock = getIntent().getBooleanExtra("adblock", true);
        String url = getIntent().getStringExtra("url");
        try {
            InputStream in = getResources().openRawResource(R.raw.pick);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            pickScript = bo.toString("UTF-8");
        } catch (Exception e) {
            pickScript = "JSON.stringify({url:location.href,title:document.title})";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FAF8F4"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(4), dp(8), dp(4));
        top.setBackgroundColor(Color.WHITE);

        Button close = new Button(this);
        close.setText("✕");
        close.setTextSize(20);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setTextColor(Color.parseColor("#17171A"));
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));

        titleView = new TextView(this);
        titleView.setTextSize(15);
        titleView.setTextColor(Color.parseColor("#17171A"));
        titleView.setSingleLine(true);
        titleView.setText("লোড হচ্ছে…");
        top.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        adButton = new Button(this);
        adButton.setAllCaps(false);
        adButton.setTextSize(13);
        styleAdButton();
        adButton.setOnClickListener(v -> {
            adblock = !adblock;
            styleAdButton();
            wv.reload();
        });
        top.addView(adButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout frame = new FrameLayout(this);
        wv = new WebView(this);
        frame.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button pick = new Button(this);
        pick.setText("＋  এই খবর নিন");
        pick.setAllCaps(false);
        pick.setTextSize(17);
        pick.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#E5391B"));
        g.setCornerRadius(dp(28));
        pick.setBackground(g);
        pick.setElevation(dp(6));
        pick.setPadding(dp(28), 0, dp(28), 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(24);
        pick.setOnClickListener(v -> wv.evaluateJavascript(pickScript, value -> {
            Intent r = new Intent();
            r.putExtra("page", value);
            setResult(RESULT_OK, r);
            finish();
        }));
        frame.addView(pick, lp);
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        wv.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (adblock && isAd(request.getUrl().getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String sch = request.getUrl().getScheme();
                if ("http".equals(sch) || "https".equals(sch)) {
                    return adblock && isAd(request.getUrl().getHost());
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                titleView.setText(view.getTitle() != null ? view.getTitle() : u);
                if (adblock) {
                    view.evaluateJavascript("(function(){var s=document.createElement('style');s.textContent='" + AD_CSS
                            + "{display:none!important;height:0!important;min-height:0!important}';(document.head||document.documentElement).appendChild(s);})()", null);
                }
            }
        });
        if (url == null) url = "https://news.google.com/?hl=bn&gl=BD&ceid=BD:bn";
        wv.loadUrl(url);
    }

    private void styleAdButton() {
        adButton.setText(adblock ? "বিজ্ঞাপন: বন্ধ" : "বিজ্ঞাপন: চালু");
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(adblock ? "#E7F4EA" : "#F1EDE6"));
        g.setCornerRadius(dp(20));
        adButton.setBackground(g);
        adButton.setTextColor(Color.parseColor(adblock ? "#1D6B33" : "#67635C"));
        adButton.setPadding(dp(12), 0, dp(12), 0);
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        if (wv != null) {
            wv.destroy();
        }
        super.onDestroy();
    }
}
