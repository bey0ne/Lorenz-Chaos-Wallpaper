package io.github.bey0ne.lorenzchaos;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Journal de la dernière erreur, lisible dans les réglages de l'appli (pas besoin d'ADB pour la retrouver).
 * Rangé dans le stockage « protégé par l'appareil » : on peut l'écrire même avant le premier déverrouillage
 * qui suit un redémarrage. Rien n'est envoyé nulle part.
 */
final class CrashLog {
    private static final String FILE = "last_error.txt";
    private static final int MAX = 8000;
    private static boolean installed;

    private CrashLog() {}

    private static File file(Context c) {
        Context d = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        return new File(d.createDeviceProtectedStorageContext().getFilesDir(), FILE);
    }

    /** Plantage non rattrapé : on l'écrit, puis Android fait comme d'habitude. */
    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            note(app, "plantage (" + thread.getName() + ")", e);
            if (previous != null) previous.uncaughtException(thread, e);
        });
    }

    /** Erreur rattrapée (le fond d'écran réessaie) ou plantage : on garde la plus récente. */
    static void note(Context c, String where, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " · " + where);
            pw.println(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ") · appli " + version(c));
            t.printStackTrace(pw);
            pw.flush();
            String s = sw.toString();
            if (s.length() > MAX) s = s.substring(0, MAX);
            File f = file(c);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(s.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // le journal ne doit jamais provoquer d'erreur lui-même
        }
    }

    static String read(Context c) {
        try {
            File f = file(c);
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        } catch (Throwable e) {
            return "";
        }
    }

    static void clear(Context c) {
        try {
            File f = file(c);
            if (f.exists() && !f.delete()) new FileOutputStream(f).close();
        } catch (Throwable ignored) {
            // rien à faire
        }
    }

    static boolean copy(Context c) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setPrimaryClip(ClipData.newPlainText("Erreur Lorenz Chaos", read(c)));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String version(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionName + " (" + (Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode) + ")";
        } catch (Throwable e) {
            return "?";
        }
    }
}
