package com.example.hagitai;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DebateActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText, subtitleText;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String[] topics = {
            "Should AI replace human creativity?",
            "Is technology making us more alone?",
            "Does social media do more harm than good?",
            "Should education be fully digital?",
            "Is climate change reversible?",
            "Is online learning better than classroom learning?"
    };

    private boolean matched = false; // 🔥 prevent double match

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debate);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        subtitleText = findViewById(R.id.subtitleText);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        findMatch();
    }

    private void findMatch() {
        String userId = auth.getCurrentUser().getUid();
        String topic = topics[new Random().nextInt(topics.length)];

        statusText.setText("Finding a Match...");
        subtitleText.setText("Searching for players...");

        // 🔍 Try to find waiting player
        db.collection("debate_matches")
                .whereEqualTo("status", "waiting")
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot doc = query.getDocuments().get(0);
                        String matchId = doc.getId();

                        doc.getReference().update(
                                "status", "matched",
                                "user2Id", userId
                        ).addOnSuccessListener(aVoid -> {
                            if (!matched) {
                                matched = true;
                                String finalTopic = doc.getString("topic");
                                goToDebateRoom(matchId, finalTopic, false);
                            }
                        });

                    } else {
                        // ❌ No match found → create new + wait
                        Map<String, Object> newMatch = new HashMap<>();
                        newMatch.put("user1Id", userId);
                        newMatch.put("status", "waiting");
                        newMatch.put("topic", topic);
                        newMatch.put("timestamp", System.currentTimeMillis());

                        db.collection("debate_matches")
                                .add(newMatch)
                                .addOnSuccessListener(docRef -> {

                                    // ⏳ Wait 5 seconds → if no match → AI
                                    new Handler().postDelayed(() -> {
                                        if (!matched) {
                                            matched = true;

                                            statusText.setText("No players found");
                                            subtitleText.setText("Starting AI debate...");

                                            goToDebateRoom(docRef.getId(), topic, true);
                                        }
                                    }, 5000);

                                    // 👂 listen for real player
                                    listenForMatch(docRef.getId());
                                });
                    }
                });
    }

    private void listenForMatch(String matchId) {
        db.collection("debate_matches").document(matchId)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists() && !matched) {
                        String status = snapshot.getString("status");

                        if ("matched".equals(status)) {
                            matched = true;

                            String topic = snapshot.getString("topic");
                            goToDebateRoom(matchId, topic, false);
                        }
                    }
                });
    }

    private void goToDebateRoom(String matchId, String topic, boolean isAI) {
        Toast.makeText(this,
                isAI ? "AI Opponent Found!" : "Player Matched!",
                Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(DebateActivity.this, DebateRoomActivity.class);
            intent.putExtra("matchId", matchId);
            intent.putExtra("topic", topic);
            intent.putExtra("isAI", isAI); // 🔥 IMPORTANT
            startActivity(intent);
            finish();
        }, 1000);
    }
}