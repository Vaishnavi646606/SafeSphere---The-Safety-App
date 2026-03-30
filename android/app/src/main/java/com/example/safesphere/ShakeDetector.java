package com.example.safesphere;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {

    public interface OnShakeListener {
        void onShakeTriggered();
    }

    private static final float SHAKE_THRESHOLD = 2.7f;
    private static final int SHAKE_SLOP_TIME_MS = 500;
    private static final int SHAKE_WINDOW_MS = 5000;
    private static final int REQUIRED_SHAKES = 5;

    private long lastShakeTimestamp = 0;
    private long firstShakeTimestamp = 0;
    private int shakeCount = 0;

    private final OnShakeListener listener;

    public ShakeDetector(OnShakeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double gX = x / SensorManager.GRAVITY_EARTH;
        double gY = y / SensorManager.GRAVITY_EARTH;
        double gZ = z / SensorManager.GRAVITY_EARTH;

        double gForce = Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        if (gForce > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();

            if (firstShakeTimestamp == 0 || (now - firstShakeTimestamp) > SHAKE_WINDOW_MS) {
                firstShakeTimestamp = now;
                shakeCount = 0;
            }

            if (lastShakeTimestamp + SHAKE_SLOP_TIME_MS < now) {
                lastShakeTimestamp = now;
                shakeCount++;

                if (shakeCount >= REQUIRED_SHAKES) {
                    shakeCount = 0;
                    firstShakeTimestamp = 0;
                    if (listener != null) listener.onShakeTriggered();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
