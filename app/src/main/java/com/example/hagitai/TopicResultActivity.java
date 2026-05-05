package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TopicResultActivity extends AppCompatActivity {

    private TextView textTopic;
    private LinearLayout btnStartDebate, btnGenerateAgain;
    private ImageView btnClose;

    private String generatedTopic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topic_result);

        textTopic        = findViewById(R.id.textTopic);
        btnStartDebate   = findViewById(R.id.btnStartDebate);
        btnGenerateAgain = findViewById(R.id.btnGenerateAgain);
        btnClose         = findViewById(R.id.btnClose);

        // Receive topic
        generatedTopic = getIntent().getStringExtra("topic");
        if (generatedTopic != null && !generatedTopic.isEmpty()) {
            textTopic.setText(generatedTopic);
        } else {
            textTopic.setText("Is AI beneficial to society?");
            generatedTopic = "Is AI beneficial to society?";
        }

        // Close button -> go home or finish
        btnClose.setOnClickListener(v -> finish());

        boolean isHumanMatch = getIntent().getBooleanExtra("isHumanMatch", false);

        // Start Debate -> FindingMatchActivity or DebateRoomActivity
        btnStartDebate.setOnClickListener(v -> {
            if (isHumanMatch) {
                Intent intent = new Intent(this, FindingMatchActivity.class);
                intent.putExtra("topic", generatedTopic);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, DebateRoomActivity.class);
                intent.putExtra("topic",        generatedTopic);
                intent.putExtra("opponentName", "Hagit AI 🤖");
                intent.putExtra("opponentLevel", "∞");
                intent.putExtra("isPractice",   true);
                startActivity(intent);
            }
            finish();
        });

        // Generate Again -> GeneratingTopicActivity
        btnGenerateAgain.setOnClickListener(v -> {
            Intent intent = new Intent(this, GeneratingTopicActivity.class);
            intent.putExtra("isHumanMatch", isHumanMatch); // Preserve mode
            startActivity(intent);
            finish();
        });
    }
}
