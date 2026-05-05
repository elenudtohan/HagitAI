package com.example.hagitai;

// ─────────────────────────────────────────────────────────────────────────────
//  SeedDummyUsers.java
//
//  Called from HomeActivity.onCreate() once. Checks before writing so it
//  never overwrites real data. Dummy UIDs start with "dummy_" — they never
//  clash with Firebase Auth UIDs.
//
//  Remove the seedIfEmpty() call from HomeActivity after your first run if
//  you want to stop seeding.
// ─────────────────────────────────────────────────────────────────────────────

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SeedDummyUsers {

    private static final String TAG = "SeedDummyUsers";

    // ── Dummy accounts ─────────────────────────────────────────────────────────
    //
    //  Covers every rank tier so the leaderboard always looks meaningful.
    //  Columns: uid | name | bio | debates | wins | xp
    //
    //  Tier thresholds (same as HomeActivity + ProfileActivity):
    //    Bronze 0–4 | Silver 5–9 | Gold 10–14 | Platinum 15–19 | Diamond 20+
    // ──────────────────────────────────────────────────────────────────────────

    private static final Object[][] DUMMY_USERS = {
            { "dummy_001", "Maria Santos",    "Logic is my weapon of choice.",               28, 22, 2200 }, // Diamond  🏆
            { "dummy_002", "James Reyes",     "I argue, therefore I am.",                    25, 18, 1800 }, // Platinum 🥈
            { "dummy_003", "Sofia Lim",       "Debate is just thinking out loud.",           20, 13, 1300 }, // Gold     🥉
            { "dummy_004", "Juan dela Cruz",   "Facts don't care about your feelings.",       18, 12, 2340 }, // Gold     4th
            { "dummy_005", "Andrea Cruz",     "Always on the right side of the argument.",   15,  8,  800 }, // Silver   5th
            { "dummy_006", "Carlo Mendoza",   "Still learning, always improving.",           12,  7,  700 }, // Silver
            { "dummy_007", "Bianca Flores",   "Newbie but fierce.",                           8,  3,  300 }, // Bronze
            { "dummy_008", "Paolo Garcia",    "Just here to win.",                            5,  2,  200 }, // Bronze
    };

    // ── Public entry point ─────────────────────────────────────────────────────

    public static void seedIfEmpty(FirebaseFirestore db) {
        for (Object[] row : DUMMY_USERS) {
            String uid = (String) row[0];
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Log.d(TAG, "Already seeded: " + uid);
                            return;
                        }
                        writeUser(db, row);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Seed check failed: " + uid, e));
        }
    }

    // ── Internal writer ────────────────────────────────────────────────────────

    private static void writeUser(FirebaseFirestore db, Object[] row) {
        String uid   = (String) row[0];
        String name  = (String) row[1];
        String bio   = (String) row[2];
        long debates = ((Number) row[3]).longValue();
        long wins    = ((Number) row[4]).longValue();
        long xp      = ((Number) row[5]).longValue();

        Map<String, Object> data = new HashMap<>();
        data.put("name",    name);
        data.put("bio",     bio);
        data.put("debates", debates);
        data.put("wins",    wins);
        data.put("xp",      xp);

        db.collection("users").document(uid).set(data)
                .addOnSuccessListener(v -> Log.d(TAG, "Seeded: " + name + " (" + uid + ")"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to seed: " + name, e));
    }
}