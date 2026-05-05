package com.example.hagitai;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    // ── Header ─────────────────────────────────────────────────────────────────
    private TextView     tvAvatar, tvUsername, tvRankBadge;
    private TextView     tvBioDisplay;
    private EditText     etBio;
    private LinearLayout layoutBioDisplay;
    private LinearLayout layoutBioEdit;
    private Button       btnSaveBio, btnCancelBio;
    private ImageView    ivHamburger, ivBack;

    // ── Stats row ──────────────────────────────────────────────────────────────
    private TextView tvDebateCount, tvWinCount, tvWinRate;

    // ── Level status card ──────────────────────────────────────────────────────
    private TextView    tvLevel, tvXP, tvNextLevel;
    private ProgressBar progressXP;

    // ── Badges ─────────────────────────────────────────────────────────────────
    private TextView tvBadgeWinsText;
    private LinearLayout bgGoldBadge;
    private ImageView ivGoldBadgeIcon;
    private TextView tvGoldBadgeText;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    // ── State ──────────────────────────────────────────────────────────────────
    private String currentBio = "";

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        bindViews();

        // ── Back button in action bar ──────────────────────────────────────────
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String profileUid = getIntent().getStringExtra("userId");
        if (profileUid == null || profileUid.isEmpty()) profileUid = user.getUid();

        boolean isOwner = profileUid.equals(user.getUid());
        setupBioInteraction(isOwner);
        loadUserProfile(profileUid);
    }

    // ── Handle action bar back press ───────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Pops back to the previous activity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── View binding ───────────────────────────────────────────────────────────

    private void bindViews() {
        tvAvatar         = findViewById(R.id.tvAvatar);
        tvUsername       = findViewById(R.id.tvUsername);
        tvBioDisplay     = findViewById(R.id.tvBioDisplay);
        etBio            = findViewById(R.id.etBio);
        layoutBioDisplay = findViewById(R.id.layoutBioDisplay);
        layoutBioEdit    = findViewById(R.id.layoutBioEdit);
        btnSaveBio       = findViewById(R.id.btnSaveBio);
        btnCancelBio     = findViewById(R.id.btnCancelBio);
        ivHamburger      = findViewById(R.id.ivHamburger);
        ivBack           = findViewById(R.id.ivBack);

        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        tvDebateCount    = findViewById(R.id.tvDebateCount);
        tvWinCount       = findViewById(R.id.tvWinCount);
        tvWinRate        = findViewById(R.id.tvWinRate);

        tvLevel          = findViewById(R.id.tvLevel);
        tvXP             = findViewById(R.id.tvXP);
        tvNextLevel      = findViewById(R.id.tvNextLevel);
        progressXP       = findViewById(R.id.progressXP);

        tvBadgeWinsText  = findViewById(R.id.tvBadgeWinsText);
        bgGoldBadge      = findViewById(R.id.bgGoldBadge);
        ivGoldBadgeIcon  = findViewById(R.id.ivGoldBadgeIcon);
        tvGoldBadgeText  = findViewById(R.id.tvGoldBadgeText);
    }

    // ── Hamburger menu moved to static screen display ──────────────────────────

    // ── Bio interaction ────────────────────────────────────────────────────────

    private void setupBioInteraction(boolean isOwner) {
        if (!isOwner) {
            layoutBioDisplay.setClickable(false);
            ImageView pencil = layoutBioDisplay.findViewById(R.id.tvBioDisplay);
            if (pencil != null) pencil.setVisibility(View.GONE);
            if (ivHamburger != null) ivHamburger.setVisibility(View.GONE);
            return;
        }

        if (ivHamburger != null)
            ivHamburger.setOnClickListener(v -> showHamburgerMenu(v));

        layoutBioDisplay.setOnClickListener(v -> enterEditMode());

        btnSaveBio.setOnClickListener(v -> {
            String newBio = etBio.getText().toString().trim();
            currentBio = newBio;
            if (newBio.isEmpty()) {
                tvBioDisplay.setText("");
                tvBioDisplay.setHint("Write something about yourself...");
            } else {
                tvBioDisplay.setText(newBio);
            }
            exitEditMode();
            persistBio(newBio);
        });

        btnCancelBio.setOnClickListener(v -> {
            etBio.setText(currentBio);
            exitEditMode();
        });
    }

    private void enterEditMode() {
        etBio.setText(currentBio);
        layoutBioDisplay.setVisibility(View.GONE);
        layoutBioEdit.setVisibility(View.VISIBLE);
        etBio.requestFocus();
        etBio.setSelection(etBio.getText().length());
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etBio, InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitEditMode() {
        layoutBioEdit.setVisibility(View.GONE);
        layoutBioDisplay.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etBio.getWindowToken(), 0);
    }

    // ── Load profile from Firestore ────────────────────────────────────────────

    private void loadUserProfile(String uid) {
        FirebaseUser fUser = mAuth.getCurrentUser();
        String fallbackName = "Hagit User";
        if (fUser != null) {
            fallbackName = fUser.getDisplayName();
            if (fallbackName == null || fallbackName.isEmpty()) fallbackName = fUser.getEmail();
        }
        final String finalFallbackName = fallbackName;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.exists() ? doc.getString("name") : null;
                    if (name == null || name.isEmpty()) name = finalFallbackName;

                    if (name != null) {
                        tvUsername.setText(name);
                        tvAvatar.setText(getInitials(name));
                    }

                    if (!doc.exists()) {
                        tvDebateCount.setText("0");
                        tvWinCount.setText("0");
                        tvWinRate.setText("0%");
                        updateLevelUI(0);
                        return;
                    }

                    String bio = doc.getString("bio");
                    currentBio = (bio != null) ? bio.trim() : "";
                    if (!currentBio.isEmpty()) {
                        tvBioDisplay.setText(currentBio);
                        etBio.setText(currentBio);
                    } else {
                        tvBioDisplay.setText("");
                        tvBioDisplay.setHint("Write something about yourself...");
                    }

                    long debates = getLong(doc, "debates");
                    long wins    = getLong(doc, "wins");
                    long xp      = getLong(doc, "xp");

                    tvDebateCount.setText(String.valueOf(debates));
                    tvWinCount.setText(String.valueOf(wins));
                    tvWinRate.setText(debates > 0
                            ? (int) ((wins * 100) / debates) + "%" : "0%");

                    updateLevelUI(xp);
                    updateBadgesUI(wins);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show());
    }

    // ── Level / rank ───────────────────────────────────────────────────────────

    private void updateLevelUI(long xp) {
        int currentLevel = (int) (xp / 200) + 1;
        long currentLevelXp = xp % 200;
        long nextLevelXpRequired = 200;

        String rank;
        if      (currentLevel < 5)  rank = "Bronze";
        else if (currentLevel < 10) rank = "Silver";
        else if (currentLevel < 15) rank = "Gold";
        else if (currentLevel < 20) rank = "Platinum";
        else                        rank = "Diamond";

        String nextRank = rank;
        if (currentLevel % 5 == 4) {
             if (rank.equals("Bronze")) nextRank = "Silver";
             else if (rank.equals("Silver")) nextRank = "Gold";
             else if (rank.equals("Gold")) nextRank = "Platinum";
             else if (rank.equals("Platinum")) nextRank = "Diamond";
        }

        int progress = (int) ((currentLevelXp * 100) / nextLevelXpRequired);
        long xpToNext = nextLevelXpRequired - currentLevelXp;

        tvXP.setText(currentLevelXp + " / " + nextLevelXpRequired + " XP");
        if (tvNextLevel != null) {
            tvNextLevel.setText(xpToNext + " XP to Level " + (currentLevel + 1) + " \u2014 " + nextRank);
        }

        tvLevel.setText("Level " + currentLevel + " \u2014 " + rank);
        progressXP.setProgress(progress);
    }

    private void updateBadgesUI(long wins) {
        if (tvBadgeWinsText != null) {
            tvBadgeWinsText.setText(wins + " Wins");
        }
        
        if (wins >= 30) {
            if (bgGoldBadge != null) bgGoldBadge.setBackgroundResource(R.drawable.bg_badge_gold);
            if (ivGoldBadgeIcon != null) ivGoldBadgeIcon.setImageTintList(null);
            if (tvGoldBadgeText != null) tvGoldBadgeText.setTextColor(android.graphics.Color.parseColor("#FFB300"));
        } else {
            if (bgGoldBadge != null) bgGoldBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            if (ivGoldBadgeIcon != null) ivGoldBadgeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4A6A8A")));
            if (tvGoldBadgeText != null) tvGoldBadgeText.setTextColor(android.graphics.Color.parseColor("#4A6A8A"));
        }
    }

    // ── Persist bio ────────────────────────────────────────────────────────────

    private void persistBio(String bio) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        db.collection("users").document(user.getUid())
                .update("bio", bio)
                .addOnSuccessListener(u ->
                        Toast.makeText(this, "Bio saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save bio.", Toast.LENGTH_SHORT).show());
    }

    // ── Hamburger Menu Popup ───────────────────────────────────────────────────

    private void showHamburgerMenu(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_profile_menu, null);
        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);

        popupWindow.setElevation(10f);

        View menuHistory = popupView.findViewById(R.id.menuHistory);
        View menuAbout = popupView.findViewById(R.id.menuAbout);
        View menuLogout = popupView.findViewById(R.id.menuLogout);

        if (menuHistory != null) {
            menuHistory.setOnClickListener(v -> {
                popupWindow.dismiss();
                startActivity(new Intent(ProfileActivity.this, RecentHistoryActivity.class));
            });
        }

        if (menuAbout != null) {
            menuAbout.setOnClickListener(v -> {
                popupWindow.dismiss();
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("About Hagit AI")
                        .setMessage("Hagit AI is a debate application developed by BS Computer Science students of Bohol Island State University – Balilihan Campus that allows users to engage in live debates, either human-to-human or human-versus-AI. It provides a fast, interactive, and user-friendly platform designed to enhance critical thinking, communication, and argumentation skills (Version 1.0).")
                        .setPositiveButton("Close", null)
                        .show();
            });
        }

        if (menuLogout != null) {
            menuLogout.setOnClickListener(v -> {
                popupWindow.dismiss();
                showLogoutConfirmation();
            });
        }

        popupWindow.showAsDropDown(anchor, 0, 10, Gravity.END);
    }

    // ── Logout confirmation ────────────────────────────────────────────────────

    private void showLogoutConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    mAuth.signOut();
                    Intent i = new Intent(ProfileActivity.this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private long getLong(com.google.firebase.firestore.DocumentSnapshot doc, String field) {
        Long val = doc.getLong(field);
        return (val != null) ? val : 0;
    }

    private String getInitials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        if (parts.length == 1 && !parts[0].isEmpty())
            return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return "?";
    }
}