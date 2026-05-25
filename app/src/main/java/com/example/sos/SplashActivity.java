package com.example.sos;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Add Rotation Animation to the Globe
        android.view.View globe = findViewById(R.id.ivGlobe);
        if (globe != null) {
            android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
                0, 360,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            );
            rotate.setDuration(4000); // 4 seconds for one full rotation
            rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
            rotate.setInterpolator(new android.view.animation.LinearInterpolator());
            globe.startAnimation(rotate);
        }

        // Wait for 3 seconds then transition
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 3000);
    }
}