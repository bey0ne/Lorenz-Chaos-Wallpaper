package io.github.bey0ne.lorenzchaos;

import android.annotation.SuppressLint;
import android.app.Presentation;
import android.app.WallpaperColors;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.Locale;

/**
 * Le fond d'écran animé : la même page que pour Wallpaper Engine sur PC, affichée par une WebView dans un
 * écran virtuel qui dessine directement sur la surface du fond d'écran. Le doigt et les capteurs sont
 * transmis à la page par window.__phone, et tout s'arrête dès que le fond d'écran n'est plus visible.
 */
public class LorenzWallpaperService extends WallpaperService {

    /** Couleurs principales de chaque palette, proposées au système pour son thème (Material You). */
    private static final int[][] COLORS = {
            {0xFF6F3BFF, 0xFFFF2BD6, 0xFF2A7BFF},   // spectre
            {0xFF00E5FF, 0xFF6F3BFF, 0xFFFF2BD6},   // néon
            {0xFF00FFA3, 0xFF00C2FF, 0xFF8A5CFF},   // aurore
            {0xFFFF8A00, 0xFFFF2A00, 0xFFFFD36B},   // braise
            {0xFF3FA9FF, 0xFF6A5CFF, 0xFFBFF8FF},   // glacier
            {0xFFFFCF5A, 0xFFFF9D2E, 0xFFC9772B},   // or
    };

    @Override
    public Engine onCreateEngine() {
        return new LorenzEngine();
    }

    private final class LorenzEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private VirtualDisplay display;
        private Presentation presentation;
        private WebView web;
        private PhoneSensors sensors;
        private SurfaceHolder holder;
        private int width, height;
        private long hiddenAt;
        private String pendingMove;

        private final Runnable flushMove = () -> {
            if (pendingMove != null) { js(pendingMove); pendingMove = null; }
        };
        private final Runnable reload = () -> {
            if (web != null) web.loadUrl(Prefs.wallpaperUrl(LorenzWallpaperService.this));
        };
        private final Runnable rebuild = () -> {
            if (holder != null && width > 0 && height > 0) build();
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
            sensors = new PhoneSensors(LorenzWallpaperService.this, this::js);
            Prefs.get(LorenzWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder surfaceHolder, int format, int w, int h) {
            super.onSurfaceChanged(surfaceHolder, format, w, h);
            holder = surfaceHolder;
            width = w;
            height = h;
            build();
        }

        @SuppressLint("SetJavaScriptEnabled")
        private void build() {
            release();
            DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            display = dm.createVirtualDisplay("lorenz-chaos", width, height,
                    getResources().getDisplayMetrics().densityDpi, holder.getSurface(), 0);
            presentation = new Presentation(LorenzWallpaperService.this, display.getDisplay());
            Window win = presentation.getWindow();
            if (win != null) win.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            web = new WebView(presentation.getContext());
            web.setBackgroundColor(Color.BLACK);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setAllowFileAccess(false);                 // les fichiers de l'appli (assets) restent lisibles
            web.setWebViewClient(new LocalOnlyClient(() -> handler.post(rebuild)));
            web.loadUrl(Prefs.wallpaperUrl(LorenzWallpaperService.this));
            presentation.setContentView(web);
            try {
                presentation.show();
            } catch (RuntimeException e) {                // écran virtuel déjà disparu (surface détruite entre-temps)
                release();
                return;
            }
            if (isVisible()) sensors.start();
            else web.onPause();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (web == null) return;
            if (visible) {
                web.onResume();
                js("window.__phone&&__phone.pause(0)");
                // retour sur l'écran d'accueil après un moment (déverrouillage, sortie d'une appli) : petit zoom d'entrée
                if (hiddenAt != 0 && SystemClock.uptimeMillis() - hiddenAt > 3000) js("window.__phone&&__phone.wake()");
                sensors.start();
            } else {
                sensors.stop();
                js("window.__phone&&__phone.pause(1)");
                web.onPause();
                hiddenAt = SystemClock.uptimeMillis();
            }
        }

        @Override
        public void onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            int kind = a == MotionEvent.ACTION_DOWN ? 0
                    : a == MotionEvent.ACTION_MOVE ? 1
                    : a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL ? 2 : -1;
            if (kind < 0 || web == null) return;
            float d = getResources().getDisplayMetrics().density;   // pixels -> pixels CSS de la page
            String call = String.format(Locale.ROOT, "window.__phone&&__phone.touch(%d,%.1f,%.1f,%d)",
                    kind, e.getX() / d, e.getY() / d, e.getEventTime());
            if (kind == 1) {
                // déplacements regroupés : au plus un envoi toutes les 8 ms
                if (pendingMove == null) handler.postDelayed(flushMove, 8);
                pendingMove = call;
            } else {
                handler.removeCallbacks(flushMove);
                flushMove.run();
                js(call);
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep, int xPixels, int yPixels) {
            js(String.format(Locale.ROOT, "window.__phone&&__phone.offset(%.4f)", xOffset));
        }

        /** Android 11+ : volet des notifications ou tiroir d'applis ouvert -> la caméra recule un peu. */
        @Override
        public void onZoomChanged(float zoom) {
            js(String.format(Locale.ROOT, "window.__phone&&__phone.zoom(%.3f)", zoom));
        }

        @Override
        public WallpaperColors onComputeColors() {
            if (Build.VERSION.SDK_INT < 27) return null;
            int p = Prefs.palette(LorenzWallpaperService.this);
            int[] c = COLORS[p >= 0 && p < COLORS.length ? p : 4];
            return new WallpaperColors(Color.valueOf(c[0]), Color.valueOf(c[1]), Color.valueOf(c[2]));
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (!Prefs.KEY.equals(key)) return;
            handler.removeCallbacks(reload);
            handler.postDelayed(reload, 400);
            if (Build.VERSION.SDK_INT >= 27) notifyColorsChanged();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder surfaceHolder) {
            release();
            holder = null;
            super.onSurfaceDestroyed(surfaceHolder);
        }

        @Override
        public void onDestroy() {
            Prefs.get(LorenzWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
            handler.removeCallbacksAndMessages(null);
            release();
            super.onDestroy();
        }

        private void js(String code) {
            if (web != null) web.evaluateJavascript(code, null);
        }

        private void release() {
            if (sensors != null) sensors.stop();
            handler.removeCallbacks(flushMove);
            pendingMove = null;
            if (presentation != null) { presentation.dismiss(); presentation = null; }
            if (web != null) { web.destroy(); web = null; }
            if (display != null) { display.release(); display = null; }
        }
    }
}
