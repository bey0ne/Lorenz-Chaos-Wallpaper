package io.github.bey0ne.lorenzchaos;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;

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
        return "file:///android_asset/index.html?mobile=1&native=1" + (q.isEmpty() ? "" : "&" + q);
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
