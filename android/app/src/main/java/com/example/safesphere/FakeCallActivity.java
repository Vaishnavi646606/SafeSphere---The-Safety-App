package com.example.safesphere;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class FakeCallActivity extends AppCompatActivity {

    private MediaPlayer ringtonePlayer;
    private boolean inCall = false;

    private TextView tvStatus;
    private Button btnAccept;
    private Button btnDecline;
    private Chronometer chrono;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call);

        tvStatus = findViewById(R.id.tvStatus);
        btnAccept = findViewById(R.id.btnAccept);
        btnDecline = findViewById(R.id.btnDecline);
        chrono = findViewById(R.id.chrono);

        startRinging();

        // Accept → call screen + timer
        btnAccept.setOnClickListener(v -> acceptCall());

        // Decline / End Call
        btnDecline.setOnClickListener(v -> endCall());
    }

    private void startRinging() {
        try {
            ringtonePlayer = MediaPlayer.create(this, R.raw.fake_ringtone);
            if (ringtonePlayer != null) {
                ringtonePlayer.setLooping(true);
                ringtonePlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void acceptCall() {
        if (inCall) return;

        inCall = true;

        // Ringtone band
        if (ringtonePlayer != null && ringtonePlayer.isPlaying()) {
            ringtonePlayer.stop();
        }

        tvStatus.setText("Call in progress…");

        // Accept button hide, decline ko "End Call" bana do
        btnAccept.setVisibility(View.GONE);
        btnDecline.setText("End Call");

        // Call timer start
        chrono.setVisibility(View.VISIBLE);
        chrono.start();
    }

    private void endCall() {
        // Ringtone stop
        try {
            if (ringtonePlayer != null) {
                if (ringtonePlayer.isPlaying()) ringtonePlayer.stop();
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
        } catch (Exception ignored) {}

        // Thoda delay deke close, taaki smooth lage
        new Handler().postDelayed(this::finish, 300);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (ringtonePlayer != null) {
                if (ringtonePlayer.isPlaying()) ringtonePlayer.stop();
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
        } catch (Exception ignored) {}
    }
}
