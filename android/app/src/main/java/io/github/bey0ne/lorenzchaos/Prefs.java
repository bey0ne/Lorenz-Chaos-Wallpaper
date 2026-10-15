package io.github.bey0ne.lorenzchaos;

import android.app.ActivityManager;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;

import java.util.Locale;

/**
 * Réglages de l'appli : une simple chaîne de requête (palette=4&count=600…) écrite par l'écran de
 * réglages (app.html) et lue telle quelle par la page du fond d'écran (index.html).
 */
final class Prefs {
    static final String KEY = "query";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("lorenz", Context.MODE_PRIVATE);
    }

    static String query(Context c) {
        return get(c).getString(KEY, "");
    }

    static void save(Context c, String q) {
        // uniquement des réglages simples : lettres, chiffres, = & . -
        String clean = q == null ? "" : q.replaceAll("[^A-Za-z0-9=&.\\-]", "");
        if (clean.length() > 2000) clean = clean.substring(0, 2000);
        if (!clean.equals(query(c))) get(c).edit().putString(KEY, clean).apply();
    }

    static String wallpaperUrl(Context c) {
        String q = query(c);
        return "file:///android_asset/index.html?mobile=1&native=1&" + device(c) + (q.isEmpty() ? "" : "&" + q);
    }

    /**
     * Ce que la page ne peut pas savoir seule, pour choisir la qualité automatiquement : mémoire (Go), cœurs,
     * fréquence de l'écran, mode économie d'énergie, téléphone à petite mémoire.
     */
    static String device(Context c) {
        int ram = 0, hz = 60;
        boolean lowRam = false, saver = false, hot = false;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram = Math.round(mi.totalMem / 1073741824f);
            lowRam = am.isLowRamDevice();
        } catch (RuntimeException ignored) {
            // valeur inconnue : la page se fie à sa puce graphique
        }
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            saver = pm != null && pm.isPowerSaveMode();
            hot = pm != null && isHot(pm);
        } catch (RuntimeException ignored) {
            // idem
        }
        try {
            DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null) hz = Math.max(30, Math.round(d.getRefreshRate()));
        } catch (RuntimeException ignored) {
            // idem
        }
        return String.format(Locale.ROOT, "ram=%d&cores=%d&hz=%d&saver=%d&lowram=%d&heat=%d",
                ram, Runtime.getRuntime().availableProcessors(), hz, saver ? 1 : 0, lowRam ? 1 : 0, hot ? 1 : 0);
    }

    /** Android 10+ : le téléphone commence à chauffer (le système réduit déjà ses performances). */
    static boolean isHot(PowerManager pm) {
        return Build.VERSION.SDK_INT >= 29 && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE;
    }

    /** Valeur numérique d'un réglage enregistré (ou valeur par défaut). */
    static float number(Context c, String key, float def) {
        for (String kv : query(c).split("&")) {
            if (kv.startsWith(key + "=")) {
                try {
                    return Float.parseFloat(kv.substring(key.length() + 1));
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        return def;
    }

    static boolean tiltOn(Context c) {
        return number(c, "tilt", 1) > 0;
    }

    static boolean shakeOn(Context c) {
        return number(c, "shake", 1) > 0;
    }

    /** Notre fond d'écran est-il celui de l'écran d'accueil ? */
    static boolean isActive(Context c) {
        try {
            WallpaperInfo info = WallpaperManager.getInstance(c).getWallpaperInfo();
            return info != null && c.getPackageName().equals(info.getPackageName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Palette choisie (0 à 5), pour les couleurs proposées au système (thème Material You). */
    static int palette(Context c) {
        for (String kv : query(c).split("&")) {
            if (kv.startsWith("palette=")) {
                try {
                    return (int) Float.parseFloat(kv.substring(8));
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        return 4;
    }
}
