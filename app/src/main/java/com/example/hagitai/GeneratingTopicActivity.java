package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class GeneratingTopicActivity extends AppCompatActivity {

    private View dot1, dot2, dot3;
    private final Handler handler = new Handler();
    private int currentDot = 0;
    private boolean topicGenerated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generating_topic);

        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);

        animateDots();
        fetchFreshTopic();
    }

    private void animateDots() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (topicGenerated) return;
                resetDots();

                View current;
                if (currentDot == 0)      current = dot1;
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

    private void fetchFreshTopic() {
        AITopicGenerator.generateFreshTopic(GeneratingTopicActivity.this, new AITopicGenerator.TopicCallback() {
            @Override
            public void onTopicGenerated(String topic) {
                runOnUiThread(() -> {
                    String finalTopic = (!TextUtils.isEmpty(topic)) ? topic : "Is AI beneficial to society?";
                    proceedToResult(finalTopic);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(GeneratingTopicActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    proceedToResult("Is AI beneficial to society?");
                });
            }
        });
    }

    private void proceedToResult(String topic) {
        if (topicGenerated) return;
        topicGenerated = true;
        handler.removeCallbacksAndMessages(null);
        resetDots();

        Intent intent = new Intent(GeneratingTopicActivity.this, TopicResultActivity.class);
        intent.putExtra("topic", topic);
        intent.putExtra("isHumanMatch", getIntent().getBooleanExtra("isHumanMatch", false));
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
