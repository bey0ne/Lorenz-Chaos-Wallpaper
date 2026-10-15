package io.github.bey0ne.lorenzchaos;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Surface;

import java.util.Locale;

/**
 * Gyroscope et accéléromètre -> page du fond d'écran (window.__phone), seulement entre start() et stop().
 * Rotation : intégrée ici entre deux envois, en radians, dans le repère de l'écran tel qu'il est tourné
 * (x à droite, y en haut, z vers soi). Secousse : pic d'accélération hors pesanteur, en m/s².
 * Sans gyroscope, la rotation est estimée à partir de la direction de la pesanteur (inclinaison seulement).
 */
final class PhoneSensors implements SensorEventListener {
    interface Sink {
        void run(String js);
    }

    private final SensorManager sm;
    private final DisplayManager dm;
    private final Sensor gyro, linear, accel;
    private final Sink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float[] grav = new float[3];
    private boolean on, gravReady;
    private long lastGyro;
    private float rx, ry, rz, peak;

    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            if (!on) return;
            send();
            handler.postDelayed(this, 16);
        }
    };

    PhoneSensors(Context c, Sink sink) {
        this.sink = sink;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        gyro = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        linear = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        accel = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    void start() {
        if (on || sm == null) return;
        on = true;
        lastGyro = 0;
        rx = ry = rz = peak = 0;
        gravReady = false;
        if (gyro != null) sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
        if (linear != null) sm.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME);
        // accéléromètre brut : pour la secousse s'il n'y a pas de capteur « linéaire », pour l'inclinaison sans gyroscope
        if ((linear == null || gyro == null) && accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        handler.postDelayed(flush, 16);
    }

    void stop() {
        if (!on) return;
        on = false;
        sm.unregisterListener(this);
        handler.removeCallbacks(flush);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        final float[] v = e.values;
        switch (e.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                if (lastGyro != 0) {
                    float dt = (e.timestamp - lastGyro) * 1e-9f;
                    if (dt > 0 && dt < 0.2f) {
                        rx += v[0] * dt;
                        ry += v[1] * dt;
                        rz += v[2] * dt;
                    }
                }
                lastGyro = e.timestamp;
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                peak = Math.max(peak, (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
                break;
            case Sensor.TYPE_ACCELEROMETER: {
                if (!gravReady) {
                    System.arraycopy(v, 0, grav, 0, 3);
                    gravReady = true;
                    break;
                }
                float gx = grav[0], gy = grav[1], gz = grav[2];
                for (int i = 0; i < 3; i++) grav[i] += (v[i] - grav[i]) * 0.15f;   // pesanteur lissée (~0,1 s)
                if (linear == null) {
                    float lx = v[0] - grav[0], ly = v[1] - grav[1], lz = v[2] - grav[2];
                    peak = Math.max(peak, (float) Math.sqrt(lx * lx + ly * ly + lz * lz));
                }
                if (gyro == null) {
                    // la pesanteur tourne en sens inverse du téléphone : rotation ≈ -(g_avant × g_après) / |g|²
                    float n2 = grav[0] * grav[0] + grav[1] * grav[1] + grav[2] * grav[2];
                    if (n2 > 1) {
                        rx -= (gy * grav[2] - gz * grav[1]) / n2;
                        ry -= (gz * grav[0] - gx * grav[2]) / n2;
                        rz -= (gx * grav[1] - gy * grav[0]) / n2;
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void send() {
        if (rx == 0 && ry == 0 && rz == 0 && peak < 12) return;
        int rot = Surface.ROTATION_0;
        Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (d != null) rot = d.getRotation();
        // axes du téléphone -> axes de l'écran (écran tourné de 90° quand le téléphone est tourné dans l'autre sens)
        float sx = rx, sy = ry;
        if (rot == Surface.ROTATION_90) { sx = -ry; sy = rx; }
        else if (rot == Surface.ROTATION_180) { sx = -rx; sy = -ry; }
        else if (rot == Surface.ROTATION_270) { sx = ry; sy = -rx; }
        sink.run(String.format(Locale.ROOT, "window.__phone&&(__phone.rot(%.5f,%.5f,%.5f),__phone.shake(%.2f))", sx, sy, rz, peak));
        rx = ry = rz = 0;
        peak = 0;
    }
}
