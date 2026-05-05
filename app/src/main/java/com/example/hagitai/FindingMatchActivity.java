package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FindingMatchActivity extends AppCompatActivity {

    // ── Dots ───────────────────────────────────────────
    private View dot1, dot2, dot3;
    private final Handler handler = new Handler();
    private int currentDot = 0;

    // ── Firebase ───────────────────────────────────────
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration matchListener;
    private String myQueueDocId;
    private boolean matchFound = false;

    // ── Timeout ────────────────────────────────────────
    private static final long MATCHMAKING_TIMEOUT_MS = 30_000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding_match);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);

        animateDots();
        joinMatchmakingQueue();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cleanupQueue();
                finish();
            }
        });

        handler.postDelayed(() -> {
            if (!matchFound) {
                cleanupQueue();
                finish();
            }
        }, MATCHMAKING_TIMEOUT_MS);
    }

    // ── DOT ANIMATION ──────────────────────────────────

    private void animateDots() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (matchFound) return;
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

    // ── MATCHMAKING ────────────────────────────────────

    private void joinMatchmakingQueue() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }

        db.collection("matchmaking")
                .whereEqualTo("status", "waiting")
                .limit(5)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<QueryDocumentSnapshot> candidates =
                            querySnapshot.getDocuments().stream()
                                    .filter(d -> !user.getUid().equals(d.getString("userId")))
                                    .map(d -> (QueryDocumentSnapshot) d)
                                    .collect(Collectors.toCollection(ArrayList::new));

                    if (!candidates.isEmpty()) {
                        claimOpponentWithTransaction(candidates.get(0).getReference(), user);
                    } else {
                        addMyselfToQueue(user);
                    }
                })
                .addOnFailureListener(e -> addMyselfToQueue(user));
    }

    private void claimOpponentWithTransaction(DocumentReference opponentRef,
                                              FirebaseUser user) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            var opponentSnap = transaction.get(opponentRef);

            if (!"waiting".equals(opponentSnap.getString("status"))) {
                throw new FirebaseFirestoreException(
                        "Opponent already matched — retry.",
                        FirebaseFirestoreException.Code.ABORTED
                );
            }

            transaction.update(opponentRef, "status", "matched");
            return null;
        }).addOnSuccessListener(unused -> {
            opponentRef.get().addOnSuccessListener(opponentDoc -> {
                // ── Null-safe reads from Firestore ─────────────────────────
                String opponentId    = safeString(opponentDoc.getString("userId"),  "unknown");
                String opponentName  = safeString(opponentDoc.getString("userName"), "Opponent");
                String opponentTopic = safeString(opponentDoc.getString("topic"),    "");

                String passedTopic   = getIntent().getStringExtra("topic");
                String resolvedTopic = !isEmpty(passedTopic)
                        ? passedTopic
                        : !isEmpty(opponentTopic)
                        ? opponentTopic
                        : "Is AI beneficial to society?";

                createDebateRoom(
                        user.getUid(), getMyName(user),
                        opponentId, opponentName,
                        resolvedTopic, opponentRef
                );
            });
        }).addOnFailureListener(e -> addMyselfToQueue(user));
    }

    private void addMyselfToQueue(FirebaseUser user) {
        if (matchFound) return;

        String passedTopic = getIntent().getStringExtra("topic");
        String topic = !isEmpty(passedTopic) ? passedTopic : "Is AI beneficial to society?";

        Map<String, Object> entry = new HashMap<>();
        entry.put("userId",     user.getUid());
        entry.put("userName",   getMyName(user));
        entry.put("status",     "waiting");
        entry.put("opponentId", ""); 
        entry.put("topic",      topic);
        entry.put("timestamp",  System.currentTimeMillis());

        db.collection("matchmaking")
                .document(user.getUid())
                .set(entry)
                .addOnSuccessListener(unused -> {
                    myQueueDocId = user.getUid();
                    listenForMatch(db.collection("matchmaking").document(user.getUid()));
                })
                .addOnFailureListener(e -> finish());
    }

    private void listenForMatch(DocumentReference myQueueDoc) {
        matchListener = myQueueDoc.addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || matchFound) return;

            String status = doc.getString("status");
            if ("matched".equals(status)) {
                String roomId = doc.getString("roomId");
                if (!isEmpty(roomId)) {
                    matchFound = true;
                    goToDebateRoom(roomId);
                }
            }
        });
    }

    private void createDebateRoom(String player1Id,   String player1Name,
                                  String player2Id,   String player2Name,
                                  String topic,       DocumentReference opponentQueueRef) {
        resolveTopic(topic, resolvedTopic -> {
            // ── All values guaranteed non-null ─────────────────────────────
            Map<String, Object> room = new HashMap<>();
            room.put("user1",        safeString(player1Id,   "unknown"));
            room.put("user1Name",    safeString(player1Name, "Player 1"));
            room.put("user2",        safeString(player2Id,   "unknown"));
            room.put("user2Name",    safeString(player2Name, "Player 2"));
            room.put("topic",        resolvedTopic);
            room.put("status",       "active");
            room.put("createdAt",    System.currentTimeMillis());

            db.collection("debates")
                    .add(room)
                    .addOnSuccessListener(roomRef -> {
                        String roomId = roomRef.getId();

                        opponentQueueRef.update(
                                "status", "matched",
                                "roomId", roomId,
                                "opponentId", player1Id
                        ).addOnSuccessListener(unused ->
                                handler.postDelayed(() -> opponentQueueRef.delete(), 3000));

                        if (!matchFound) {
                            matchFound = true;
                            goToDebateRoom(roomId);
                        }
                    })
                    .addOnFailureListener(e -> finish());
        });
    }

    private void resolveTopic(String topic, TopicCallback callback) {
        if (!isEmpty(topic)) {
            callback.onResolved(topic);
            return;
        }

        String today = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.getDefault()
        ).format(new java.util.Date());

        db.collection("topics").document(today).get()
                .addOnSuccessListener(doc -> {
                    String fetched = doc.exists() ? doc.getString("topic") : null;
                    callback.onResolved(!isEmpty(fetched)
                            ? fetched : "Is AI beneficial to society?");
                })
                .addOnFailureListener(e ->
                        callback.onResolved("Is AI beneficial to society?"));
    }

    interface TopicCallback {
        void onResolved(String topic);
    }

    // ── NAVIGATION ─────────────────────────────────────

    private void goToDebateRoom(String roomId) {
        if (isFinishing() || isDestroyed()) return;
        handler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(this, MatchFoundActivity.class);
        intent.putExtra("roomId", roomId);
        startActivity(intent);
        finish();
    }

    // ── HELPERS ────────────────────────────────────────

    /** Returns display name, email, or "Unknown" — never null. */
    private String getMyName(FirebaseUser user) {
        String name = user.getDisplayName();
        if (!isEmpty(name)) return name;
        String email = user.getEmail();
        if (!isEmpty(email)) return email;
        return "Unknown";
    }

    /** Returns value if non-empty, otherwise fallback — never null. */
    private String safeString(String value, String fallback) {
        return (!isEmpty(value)) ? value : fallback;
    }

    /** True if string is null or empty. */
    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private void cleanupQueue() {
        if (!matchFound && myQueueDocId != null) {
            db.collection("matchmaking").document(myQueueDocId).delete();
            myQueueDocId = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (matchListener != null) matchListener.remove();
        cleanupQueue();
    }
}