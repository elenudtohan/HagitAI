package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PracticeActivity extends AppCompatActivity {

    private View dot1, dot2, dot3;
    private TextView textStatus;
    private final Handler handler = new Handler();
    private int currentDot = 0;
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding_match); // reuse the same loading UI

        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);

        // Override the text labels to show practice context
        TextView title = findViewById(R.id.textFinding);
        TextView sub   = findViewById(R.id.textSearching);
        if (title != null) title.setText("Practice Mode");
        if (sub   != null) sub.setText("Generating your debate topic with AI...");

        animateDots();
        generateTopicAndStart();
    }

    // ── DOT ANIMATION ──────────────────────────────────
    private void animateDots() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (launched) return;
                resetDots();

                View current;
                if      (currentDot == 0) current = dot1;
                else if (currentDot == 1) current = dot2;
                else                      current = dot3;

                AlphaAnimation blink = new AlphaAnimation(0.3f, 1.0f);
                blink.setDuration(400);
                blink.setRepeatCount(0);
                current.startAnimation(blink);

                currentDot = (currentDot + 1) % 3;
                handler.postDelayed(this, 500);
            }
        };
        handler.post(runnable);
    }

    private void resetDots() {
        dot1.clearAnimation();
        dot2.clearAnimation();
        dot3.clearAnimation();
    }

    // ── GENERATE TOPIC VIA GEMINI ──────────────────────
    private void generateTopicAndStart() {
        AITopicGenerator.generateDailyTopic(PracticeActivity.this, new AITopicGenerator.TopicCallback() {
            @Override
            public void onTopicGenerated(String topic) {
                launchDebateRoom(topic);
            }

            @Override
            public void onError(String errorMessage) {
                launchDebateRoom("Is AI beneficial to society?");
            }
        });
    }

    // ── LAUNCH DEBATE ROOM ─────────────────────────────
    private void launchDebateRoom(String topic) {
        if (launched) return;
        launched = true;
        handler.removeCallbacksAndMessages(null);

        Intent intent = new Intent(this, DebateRoomActivity.class);
        intent.putExtra("topic",        topic);
        intent.putExtra("opponentName", "Hagit AI 🤖");
        intent.putExtra("opponentLevel","∞");
        intent.putExtra("isPractice",   true);   // ← key flag for AI responses
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}