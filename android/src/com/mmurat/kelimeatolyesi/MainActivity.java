package com.mmurat.kelimeatolyesi;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int ZEMIN_ACIK = 0xFFEDF0F5;
    private static final int ZEMIN_KOYU = 0xFF0F121A;

    private FrameLayout kok;
    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsHazir = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.UK);
                    tts.setSpeechRate(0.9f);
                    ttsHazir = true;
                }
            }
        });

        webView = new WebView(this);
        WebSettings ayarlar = webView.getSettings();
        ayarlar.setJavaScriptEnabled(true);
        ayarlar.setDomStorageEnabled(true);
        ayarlar.setTextZoom(100);

        webView.addJavascriptInterface(new SesKoprusu(), "AndroidTTS");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                // Dış bağlantıları tarayıcıda aç
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        kok = new FrameLayout(this);
        kok.addView(webView);
        setContentView(kok);
        kenardanKenaraAyarla();
        renkleriUygula();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    // Android 15+ uygulamayı sistem çubuklarının altına çizer; içeriği çubukların dışında tut
    private void kenardanKenaraAyarla() {
        if (Build.VERSION.SDK_INT < 30) return;
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        kok.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            }
        });
    }

    private boolean geceModu() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void renkleriUygula() {
        boolean gece = geceModu();
        int zemin = gece ? ZEMIN_KOYU : ZEMIN_ACIK;
        kok.setBackgroundColor(zemin);
        webView.setBackgroundColor(zemin);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int acikCubuklar = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(gece ? 0 : acikCubuklar, acikCubuklar);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration yeni) {
        super.onConfigurationChanged(yeni);
        renkleriUygula();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private class SesKoprusu {
        @JavascriptInterface
        public void speak(String metin) {
            if (ttsHazir && metin != null) {
                tts.speak(metin, TextToSpeech.QUEUE_FLUSH, null, "kelime");
            }
        }
    }
}
