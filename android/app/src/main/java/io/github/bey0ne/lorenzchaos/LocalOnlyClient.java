package io.github.bey0ne.lorenzchaos;

import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * N'ouvre que les pages de l'appli. Si le moteur de rendu de la WebView s'arrête (manque de mémoire,
 * pilote graphique…), on la remplace au lieu de laisser planter toute l'appli.
 */
class LocalOnlyClient extends WebViewClient {
    private final Runnable onCrash;

    LocalOnlyClient(Runnable onCrash) {
        this.onCrash = onCrash;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return !request.getUrl().toString().startsWith("file:///android_asset/");
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        if (onCrash != null) onCrash.run();
        return true;
    }
}
