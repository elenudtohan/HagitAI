package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ImageView bisuLogo;
    private TextView appNameText, sloganText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();

        bisuLogo = findViewById(R.id.bisuLogo);
        appNameText = findViewById(R.id.appNameText);
        sloganText = findViewById(R.id.sloganText);

        // Fade-in animation
        bisuLogo.setAlpha(0f);
        appNameText.setAlpha(0f);
        sloganText.setAlpha(0f);

        bisuLogo.animate().alpha(1f).setDuration(1200).start();
        appNameText.animate().alpha(1f).setDuration(1600).start();
        sloganText.animate().alpha(1f).setDuration(2000).start();

        // After 3 seconds, check login state
        new Handler().postDelayed(() -> {
            if (mAuth.getCurrentUser() != null) {
                startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            }
            finish();
        }, 3000);
    }
}
