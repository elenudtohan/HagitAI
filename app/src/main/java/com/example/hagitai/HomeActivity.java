package com.example.hagitai;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private TextView welcomeText, subText;
    private TextView levelText, xpBox;
    private TextView topicTitle, topicDate;
    private LinearLayout quickDebateBtn, practiceBtn, randomBtn;
    private LinearLayout profileBtn, leaderboardBtn;
    private TextView seeAllRanking;

    private DrawerLayout drawerLayout;
    private android.widget.ImageView ivHamburger;
    private LinearLayout btnHistory, btnLogout;
    private TextView tvDrawerAvatar, txtUser;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration topicListener;

    private String currentTopicStr = "Is AI beneficial to society?";
    private String currentUserId = "";
    private long currentUserXp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupDrawer();
        setupButtons();

        FirebaseUser user = mAuth.getCurrentUser();

        // 🔥 DEMO MODE SWITCH
        if (Config.USE_DEMO_DATA) {

            welcomeText.setText("Welcome, " + UserData.name + " 👋");

            if (txtUser != null) txtUser.setText(UserData.name);

            if (tvDrawerAvatar != null && !UserData.name.isEmpty())
                tvDrawerAvatar.setText(String.valueOf(UserData.name.charAt(0)).toUpperCase());

        } else {

            if (user != null) {
                currentUserId = user.getUid();

                String name = user.getDisplayName();
                if (name == null || name.isEmpty()) name = user.getEmail();

                welcomeText.setText("Welcome, " + name + " 👋");

                if (txtUser != null) txtUser.setText(name);

                if (tvDrawerAvatar != null && name != null && !name.isEmpty())
                    tvDrawerAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
            } else {
                welcomeText.setText("Welcome to Hagit.AI 🌸");
            }
        }

        topicDate.setText(new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date()));

        // SeedDummyUsers.seedIfEmpty(db);

        loadUserStats();
        loadTopicOfTheDay();
        loadLeaderboard();
    }

    private void bindViews() {
        profileBtn = findViewById(R.id.profileBtn);
        welcomeText = findViewById(R.id.welcomeText);
        subText = findViewById(R.id.subText);
        levelText = findViewById(R.id.levelText);
        xpBox = findViewById(R.id.xpBox);
        topicTitle = findViewById(R.id.topicTitle);
        topicDate = findViewById(R.id.topicDate);
        quickDebateBtn = findViewById(R.id.quickDebateBtn);
        practiceBtn = findViewById(R.id.practiceBtn);
        randomBtn = findViewById(R.id.randomBtn);
        leaderboardBtn = findViewById(R.id.leaderboardBtn);
        seeAllRanking = findViewById(R.id.seeAllRanking);

        drawerLayout = findViewById(R.id.drawerLayout);
        ivHamburger = findViewById(R.id.ivHamburger);
        btnHistory = findViewById(R.id.btnHistory);
        btnLogout = findViewById(R.id.btnLogout);
        tvDrawerAvatar = findViewById(R.id.tvDrawerAvatar);
        txtUser = findViewById(R.id.txtUser);
    }

    private void setupDrawer() {
        if (ivHamburger != null)
            ivHamburger.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.END);
            });

        if (btnLogout != null)
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Intent i = new Intent(HomeActivity.this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            });

        if (btnHistory != null)
            btnHistory.setOnClickListener(v -> {
                startActivity(new Intent(HomeActivity.this, RecentHistoryActivity.class));
                drawerLayout.closeDrawers();
            });
    }

    private void setupButtons() {
        if (leaderboardBtn != null)
            leaderboardBtn.setOnClickListener(v ->
                    startActivity(new Intent(HomeActivity.this, LeaderboardActivity.class)));

        if (seeAllRanking != null)
            seeAllRanking.setOnClickListener(v ->
                    startActivity(new Intent(this, LeaderboardActivity.class)));

        quickDebateBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, FindingMatchActivity.class);
            i.putExtra("topic", currentTopicStr);
            startActivity(i);
        });

        practiceBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, DebateRoomActivity.class);
            i.putExtra("topic", currentTopicStr);
            i.putExtra("opponentName", "Hagit AI 🤖");
            i.putExtra("opponentLevel", "∞");
            i.putExtra("isPractice", true);
            startActivity(i);
        });

        randomBtn.setOnClickListener(v ->
                startActivity(new Intent(this, RandomtopicActivity.class)));

        profileBtn.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadUserStats(); // Reloads XP, Level, and triggers loadLeaderboard() internally
        }
    }

    private void loadUserStats() {

        // 🔥 DEMO MODE
        if (Config.USE_DEMO_DATA) {
            xpBox.setText(UserData.xp + " XP");
            levelText.setText("Level " + UserData.level + " — " + UserData.rank);
            return;
        }

        if (currentUserId.isEmpty()) return;

        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    FirebaseUser fUser = mAuth.getCurrentUser();
                    String finalName = "Hagit User";
                    if (fUser != null) {
                        finalName = fUser.getDisplayName();
                        if (finalName == null || finalName.isEmpty()) finalName = fUser.getEmail();
                    }
                    
                    if (!doc.exists() || !doc.contains("name")) {
                        java.util.Map<String, Object> freshData = new java.util.HashMap<>();
                        freshData.put("name", finalName);
                        if (!doc.exists()) {
                            freshData.put("xp", 0L);
                            freshData.put("wins", 0L);
                            freshData.put("losses", 0L);
                            freshData.put("debates", 0L);
                        }
                        db.collection("users").document(currentUserId).set(freshData, com.google.firebase.firestore.SetOptions.merge());
                    }

                    Long xpRaw = doc.getLong("xp");
                    Long winsRaw = doc.getLong("wins");

                    long xp = (xpRaw != null) ? xpRaw : 0;
                    long wins = (winsRaw != null) ? winsRaw : 0;

                    currentUserXp = xp;

                    xpBox.setText(xp + " XP");

                    int currentLevel = (int) (xp / 200) + 1;
                    String rankName;
                    if      (currentLevel < 5)  rankName = "Bronze";
                    else if (currentLevel < 10) rankName = "Silver";
                    else if (currentLevel < 15) rankName = "Gold";
                    else if (currentLevel < 20) rankName = "Platinum";
                    else                        rankName = "Diamond";

                    levelText.setText("Level " + currentLevel + " — " + rankName);

                    loadLeaderboard();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load user stats", e));
    }

    private void loadTopicOfTheDay() {

        // 🔥 DEMO MODE
        if (Config.USE_DEMO_DATA) {
            currentTopicStr = UserData.demoTopic;
            topicTitle.setText(UserData.demoTopic);
            return;
        }

        topicTitle.setText("Loading topic...");

        AITopicGenerator.generateDailyTopic(this, new AITopicGenerator.TopicCallback() {
            @Override
            public void onTopicGenerated(String topic) {
                runOnUiThread(() -> {
                    if (topic != null && !topic.isEmpty()) {
                        currentTopicStr = topic;
                        topicTitle.setText(topic);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    currentTopicStr = "Is AI beneficial to society?";
                    topicTitle.setText(currentTopicStr);
                });
            }
        });
    }

    private void loadLeaderboard() {
        db.collection("users")
                .orderBy("wins", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int i = 0;


                    
                    for (var doc : querySnapshot) {
                        if (i >= 5) break;

                        String name = doc.getString("name");
                        Long xpL = doc.getLong("xp");
                        Long winsL = doc.getLong("wins");

                        long xp = (xpL != null) ? xpL : 0;
                        long wins = (winsL != null) ? winsL : 0;
                        String dispName = (name != null) ? name : "Unknown";

                        int currentLevel = (int) (xp / 200) + 1;
                        String rankName;
                        if      (currentLevel < 5)  rankName = "Bronze";
                        else if (currentLevel < 10) rankName = "Silver";
                        else if (currentLevel < 15) rankName = "Gold";
                        else if (currentLevel < 20) rankName = "Platinum";
                        else                        rankName = "Diamond";

                        String subtitle = "Level " + currentLevel + " · " + rankName;

                        int pos = i + 1;
                        int nameId = res("rank" + pos + "Name");
                        int subtitleId = res("rank" + pos + "Subtitle");
                        int xpId = res("rank" + pos + "XP");

                        TextView tvName = findViewById(nameId);
                        TextView tvSubtitle = findViewById(subtitleId);
                        TextView tvXP = findViewById(xpId);

                        if (tvName != null) tvName.setText(dispName);
                        if (tvSubtitle != null) tvSubtitle.setText(subtitle);
                        if (tvXP != null) tvXP.setText(xp + " XP");

                        i++;
                    }
                });
    }

    private int res(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}