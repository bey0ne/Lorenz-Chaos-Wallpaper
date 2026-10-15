package io.github.bey0ne.lorenzchaos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Toast;

import java.util.Locale;

/**
 * L'appli : aperçu du fond d'écran en plein écran, réglages et bouton « Appliquer ». L'interface est la page
 * app.html (le fond d'écran y tourne dans un cadre) ; elle enregistre ses réglages par window.Android.
 */
public class MainActivity extends Activity {
    private WebView web;
    private PhoneSensors sensors;
    private volatile float insetTop, insetBottom;
    private boolean sheetOpen;
    private Object backCallback;                         // OnBackInvokedCallback (Android 13+)

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Window w = getWindow();
        // l'aperçu passe sous les barres du système ; la page garde ses boutons hors de leur chemin
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        else w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        web.setWebViewClient(new LocalOnlyClient(() -> web.post(this::recreate)));
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            float d = getResources().getDisplayMetrics().density;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetTop = i.top / d;
                insetBottom = i.bottom / d;
            } else {
                insetTop = insets.getSystemWindowInsetTop() / d;
                insetBottom = insets.getSystemWindowInsetBottom() / d;
            }
            js(String.format(Locale.ROOT, "window.setInsets&&setInsets(%.1f,%.1f)", insetTop, insetBottom));
            return insets;
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/app.html");
        sensors = new PhoneSensors(this, this::js);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        sensors.start();
        js("window.onAppResume&&onAppResume()");          // l'état « fond d'écran actif » a pu changer
    }

    @Override
    protected void onPause() {
        sensors.stop();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sensors.stop();
        web.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {                         // Android 12 et avant
        if (sheetOpen) js("window.closeSheet&&closeSheet()");
        else super.onBackPressed();
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    /** Retour arrière : ferme d'abord le panneau des réglages s'il est ouvert. */
    private void setSheetOpen(boolean open) {
        sheetOpen = open;
        if (Build.VERSION.SDK_INT < 33) return;
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (open && backCallback == null) {
            OnBackInvokedCallback cb = () -> js("window.closeSheet&&closeSheet()");
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        } else if (!open && backCallback != null) {
            dispatcher.unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }

    private void openPicker() {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(this, LorenzWallpaperService.class));
        try {
            startActivity(i);
            return;
        } catch (RuntimeException ignored) {
            // certains téléphones n'ont pas cet écran : on ouvre la liste des fonds animés
        }
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Ce téléphone ne permet pas d'ouvrir le choix des fonds d'écran animés.", Toast.LENGTH_LONG).show();
        }
    }

    /** Appels depuis app.html (fil d'exécution de la WebView, d'où les runOnUiThread). */
    private final class Bridge {
        @JavascriptInterface
        public String query() {
            return Prefs.query(MainActivity.this);
        }

        @JavascriptInterface
        public void save(String q) {
            Prefs.save(MainActivity.this, q);
        }

        @JavascriptInterface
        public boolean active() {
            return Prefs.isActive(MainActivity.this);
        }

        @JavascriptInterface
        public String insets() {
            return String.format(Locale.ROOT, "[%.1f,%.1f]", insetTop, insetBottom);
        }

        @JavascriptInterface
        public void apply() {
            runOnUiThread(MainActivity.this::openPicker);
        }

        @JavascriptInterface
        public void sheet(boolean open) {
            runOnUiThread(() -> setSheetOpen(open));
        }
    }
}
