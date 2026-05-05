package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class RandomtopicActivity extends AppCompatActivity {

    private LinearLayout btnAI, btnHuman;

    private String[] randomTopics = {
        "Are social media platforms more harmful than beneficial?",
        "Should homework be banned in schools?",
        "Is artificial intelligence a threat to humanity?",
        "Should voting be made mandatory?",
        "Does technology isolate us more than it connects us?",
        "Should university education be totally free?",
        "Is space exploration worth the massive cost?",
        "Should a four-day work week be standard?",
        "Are single-sex schools more effective than co-ed schools?",
        "Should the drinking age be lowered?",
        "Is remote work better than working in an office?",
        "Should animal testing be completely banned?",
        "Is climate change the greatest threat to humanity?",
        "Should healthcare be a universal right?",
        "Do violent video games cause violent behavior?",
        "Should all zoos be closed?",
        "Is a cashless society a good idea?",
        "Should standardized testing be eliminated?",
        "Are electric cars really the future of transportation?",
        "Should genetically modified organisms (GMOs) be banned?"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_random_topic);

        btnAI      = findViewById(R.id.btnAI);
        btnHuman   = findViewById(R.id.btnHuman);

        btnHuman.setOnClickListener(v -> {
            String randomTopic = randomTopics[new java.util.Random().nextInt(randomTopics.length)];
            Intent intent = new Intent(this, FindingMatchActivity.class);
            intent.putExtra("topic", randomTopic);
            startActivity(intent);
            finish();
        });

        btnAI.setOnClickListener(v -> {
            Intent intent = new Intent(this, GeneratingTopicActivity.class);
            intent.putExtra("isHumanMatch", false);
            startActivity(intent);
            finish();
        });
    }
}