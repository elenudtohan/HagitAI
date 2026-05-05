package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private ImageView hagitLogo, bisuLogo;
    private TextView taglineText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase first
        FirebaseApp.initializeApp(this);
        checkFirebaseConnection();

        // Initialize views
        hagitLogo = findViewById(R.id.hagitLogo);
        taglineText = findViewById(R.id.taglineText);

        // Load fade-in animation
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        // Apply animation to logos and tagline
        hagitLogo.startAnimation(fadeIn);
        bisuLogo.startAnimation(fadeIn);
        taglineText.startAnimation(fadeIn);

        // Delay before navigating to LoginActivity (3 seconds)
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }, 3000);
    }

    private void checkFirebaseConnection() {
        FirebaseDatabase.getInstance().getReference(".info/connected")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Boolean connected = snapshot.getValue(Boolean.class);
                        if (connected != null && connected) {
                            Log.d("Firebase", "✅ Connected to Firebase Realtime Database");
                        } else {
                            Log.w("Firebase", "⚠️ Not connected to Firebase yet");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("Firebase", "❌ Connection check cancelled: " + error.getMessage());
                    }
                });
    }
}
