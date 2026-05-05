package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.net.URL;
import java.net.HttpURLConnection;

public class DebateResultActivity extends AppCompatActivity {

    private static final String TAG = "DebateResultActivity";

    private TextView textScore, textScoreLabel, textXP, textAIFeedback, textLevelBadge;
    private TextView textNextLevelTitle, textCurrentLevelXP, textNextLevelXP;
    private TextView textMotivation, textLogicValue, textPersuasivenessValue, textCreativityValue;
    private ProgressBar progressLevel, progressLogic, progressPersuasiveness, progressCreativity;
    private LinearLayout btnExit, btnDebateAgain;

    private String roomId, topic, history;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debate_result);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI
        textScore = findViewById(R.id.textScore);
        textScoreLabel = findViewById(R.id.textScoreLabel);
        textXP = findViewById(R.id.textXP);
        textAIFeedback = findViewById(R.id.textAIFeedback);
        textLevelBadge = findViewById(R.id.textLevelBadge);
        textNextLevelTitle = findViewById(R.id.textNextLevelTitle);
        textCurrentLevelXP = findViewById(R.id.textCurrentLevelXP);
        textNextLevelXP = findViewById(R.id.textNextLevelXP);
        textMotivation = findViewById(R.id.textMotivation);
        textLogicValue = findViewById(R.id.textLogicValue);
        textPersuasivenessValue = findViewById(R.id.textPersuasivenessValue);
        textCreativityValue = findViewById(R.id.textCreativityValue);

        progressLevel = findViewById(R.id.progressLevel);
        progressLogic = findViewById(R.id.progressLogic);
        progressPersuasiveness = findViewById(R.id.progressPersuasiveness);
        progressCreativity = findViewById(R.id.progressCreativity);

        btnExit = findViewById(R.id.btnExit);
        btnDebateAgain = findViewById(R.id.btnDebateAgain);

        roomId = getIntent().getStringExtra("roomId");
        if (roomId == null) roomId = getIntent().getStringExtra("matchId");
        topic = getIntent().getStringExtra("topic");
        history = getIntent().getStringExtra("history");

        String judgeResult = getIntent().getStringExtra("judgeResult");

        btnExit.setOnClickListener(v -> goHome());
        btnDebateAgain.setOnClickListener(v -> {
            startActivity(new Intent(this, FindingMatchActivity.class));
            finish();
        });

        // Set Loading State
        textScore.setText("--");
        textScoreLabel.setText("Evaluating");
        textXP.setText("...");
        textLogicValue.setText("--%");
        textPersuasivenessValue.setText("--%");
        textCreativityValue.setText("--%");
        progressLogic.setProgress(0);
        progressPersuasiveness.setProgress(0);
        progressCreativity.setProgress(0);
        textAIFeedback.setText("The Arbiter is analyzing your arguments...");

        if (judgeResult != null) {
            applyJudgeResult(judgeResult);
        } else if (history != null && !history.trim().isEmpty()) {
            performAIJudging(history);
        } else {
            textAIFeedback.setText("No debate history available for evaluation.");
            displayResults("Draw", 0, 0, 0, 0, "No arguments were presented. The debate ended in a draw with no score.");
        }
    }

    private void performAIJudging(String history) {
        new Thread(() -> {
            try {
                String apiKey = ApiKeyLoader.getApiKey(DebateResultActivity.this);
                if (apiKey == null || apiKey.isEmpty()) {
                    runOnUiThread(() -> displayResults("Opponent", 50, 50, 50, 10, "OpenAI API Key is missing."));
                    return;
                }

                String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String prompt = "Evaluate this debate fairly and objectively based ONLY on actual messages sent in the debate. "
                        + "If both participants did not send any arguments, you MUST return winner as 'Draw' with 0 for logic, persuasiveness, and creativity. "
                        + "Otherwise, evaluate both participants. Decide the winner (User, Opponent, or Draw), give scores (0-100 integer), and short feedback. "
                        + "Return ONLY a valid raw JSON object with keys: 'winner' (User, Opponent, or Draw), 'logic' (0-100 integer), 'persuasiveness' (0-100 integer), 'creativity' (0-100 integer), 'feedback' (string). "
                        + "Do NOT wrap the JSON in Markdown formatting like ```json. Return just the raw `{ ... }` structure. "
                        + "Debate History:\n" + history;

                JSONObject requestBody = new JSONObject();
                JSONObject messageObj = new JSONObject();
                org.json.JSONArray messagesArr = new org.json.JSONArray();
                messageObj.put("role", "user");
                messageObj.put("content", prompt);
                messagesArr.put(messageObj);
                requestBody.put("model", "llama-3.1-8b-instant");
                requestBody.put("messages", messagesArr);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes("UTF-8"));
                }

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JSONObject res = new JSONObject(response.toString());
                    String aiText = res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                    
                    if (aiText.contains("```json")) {
                        aiText = aiText.substring(aiText.indexOf("```json") + 7, aiText.lastIndexOf("```"));
                    } else if (aiText.contains("```")) {
                        aiText = aiText.substring(aiText.indexOf("```") + 3, aiText.lastIndexOf("```"));
                    }

                    JSONObject resultObj = new JSONObject(aiText.trim());
                    String winner = resultObj.optString("winner", "Opponent");
                    int logic = resultObj.optInt("logic", 50);
                    int pers = resultObj.optInt("persuasiveness", 50);
                    int creat = resultObj.optInt("creativity", 50);
                    final int finalXp;
                    if (winner.equalsIgnoreCase("User")) finalXp = 50;
                    else if (winner.equalsIgnoreCase("Opponent")) finalXp = 20;
                    else finalXp = 10; // Draw
                    String feedback = resultObj.optString("feedback", "Good effort!");

                    runOnUiThread(() -> displayResults(winner, logic, pers, creat, finalXp, feedback));
                } else {
                    runOnUiThread(() -> displayResults("Draw", 50, 50, 50, 10, "Great effort! Evaluation was substituted due to an error reaching the AI judge."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Judging failed", e);
                runOnUiThread(() -> displayResults("Draw", 50, 50, 50, 10, "Great effort! Evaluation was substituted due to a parsing or connection error."));
            }
        }).start();
    }

    // 🔥 APPLY RESULT FROM DebateRoomActivity
    private void applyJudgeResult(String judgeResult) {
        try {
            Log.d("JUDGE_RESULT", judgeResult);

            String winner = judgeResult.contains("Winner: User") ? "User" : "Opponent";

            String reason = judgeResult.contains("Reason:")
                    ? judgeResult.split("Reason:")[1].trim()
                    : "Good debate.";

            int logic = winner.equals("User") ? 85 : 65;
            int pers = winner.equals("User") ? 85 : 65;
            int creat = winner.equals("User") ? 85 : 65;
            int xp = winner.equals("User") ? 50 : 20;

            displayResults(winner, logic, pers, creat, xp, reason);

        } catch (Exception e) {
            textAIFeedback.setText("Error applying judge result.");
        }
    }

    private void displayResults(String winner, int logic, int pers, int creat, int xp, String feedback) {
        boolean isUserWinner = winner.equalsIgnoreCase("User");
        boolean isDraw = winner.equalsIgnoreCase("Draw");
        int overallScore = (logic + pers + creat) / 3;

        textScore.setText(String.valueOf(overallScore));

        if (isDraw) {
            textScoreLabel.setText("DRAW 🤝");
            textScore.setTextColor(0xFFFFC107);
            textMotivation.setText("No significant advantage. It's a tie.");
            textMotivation.setTextColor(0xFFFFC107);
        } else if (isUserWinner) {
            textScoreLabel.setText("VICTORY 🏆");
            textScore.setTextColor(0xFF00E676);
            textMotivation.setText("🔥 Amazing! You won the debate.");
            textMotivation.setTextColor(0xFF00E676);
        } else {
            textScoreLabel.setText("DEFEAT 💀");
            textScore.setTextColor(0xFFFF8A65);
            textMotivation.setText("💪 Great effort! Keep improving!");
            textMotivation.setTextColor(0xFFFF8A65);
        }

        progressLogic.setProgress(logic);
        textLogicValue.setText(String.valueOf(logic));

        progressPersuasiveness.setProgress(pers);
        textPersuasivenessValue.setText(String.valueOf(pers));

        progressCreativity.setProgress(creat);
        textCreativityValue.setText(String.valueOf(creat));

        textXP.setText("+" + xp + " XP Earned");
        textAIFeedback.setText(feedback);

        updateUserStats(isUserWinner, xp, isDraw);
        saveHistory(isUserWinner, isDraw);
        saveResultToDebates(winner, logic, pers, creat, xp, feedback);
    }

    private void saveResultToDebates(String winner, int logic, int pers, int creat, int xp, String feedback) {
        if (roomId == null || roomId.isEmpty()) return;

        Map<String, Object> result = new HashMap<>();
        result.put("winner", winner);
        result.put("logic", logic);
        result.put("persuasiveness", pers);
        result.put("creativity", creat);
        result.put("xpEarned", xp);
        result.put("feedback", feedback);

        db.collection("debates").document(roomId).update("result", result);
    }

    private void updateUserStats(boolean won, int earnedXp, boolean isDraw) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    long currentXp = documentSnapshot.contains("xp") ? documentSnapshot.getLong("xp") : 0;
                    long wins = documentSnapshot.contains("wins") ? documentSnapshot.getLong("wins") : 0;
                    long losses = documentSnapshot.contains("losses") ? documentSnapshot.getLong("losses") : 0;

                    long newXp = currentXp + earnedXp;
                    if (!isDraw) {
                        if (won) wins++; else losses++;
                    }

                    long level = (newXp / 200) + 1;
                    long currentLevelXp = newXp % 200;
                    long nextLevelXpRequired = 200;
                    int progress = (int) ((currentLevelXp * 100) / nextLevelXpRequired);

                    String rank;
                    if      (level < 5)  rank = "Bronze";
                    else if (level < 10) rank = "Silver";
                    else if (level < 15) rank = "Gold";
                    else if (level < 20) rank = "Platinum";
                    else                 rank = "Diamond";

                    textLevelBadge.setText("Level " + level + " — " + rank);
                    progressLevel.setProgress(progress);

                    if (textNextLevelTitle != null) {
                        textNextLevelTitle.setText("Progress to Level " + (level + 1));
                    }
                    if (textCurrentLevelXP != null) {
                        textCurrentLevelXP.setText(currentLevelXp + " XP");
                    }
                    if (textNextLevelXP != null) {
                        textNextLevelXP.setText(nextLevelXpRequired + " XP");
                    }

                    Map<String, Object> update = new HashMap<>();
                    update.put("xp", newXp);
                    update.put("wins", wins);
                    update.put("losses", losses);
                    update.put("debates", wins + losses + (isDraw ? 1 : 0));

                    db.collection("users").document(user.getUid()).set(update, SetOptions.merge());
                });
    }

    private void saveHistory(boolean won, boolean isDraw) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String outcome = isDraw ? "Draw" : (won ? "Win" : "Loss");

        Map<String, Object> historyData = new HashMap<>();
        historyData.put("uid", user.getUid());
        historyData.put("topic", topic != null ? topic : "Random Topic");
        historyData.put("opponent", "AI/Match");
        historyData.put("result", outcome);
        historyData.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        historyData.put("timestamp", System.currentTimeMillis());
        historyData.put("chatHistory", history != null ? history : "");

        db.collection("debate_history").add(historyData);
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}