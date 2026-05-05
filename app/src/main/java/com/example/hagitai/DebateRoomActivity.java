package com.example.hagitai;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DebateRoomActivity extends AppCompatActivity {

    // ── Header ────────────────────────────────────────────────────────────────
    private TextView textTimer;

    // ── Opponent card ─────────────────────────────────────────────────────────
    private TextView textParticipantName, textParticipantCourse, textOnlineStatus;

    // ── Topic card ────────────────────────────────────────────────────────────
    private TextView textDebateTopic;

    // ── Chat area ─────────────────────────────────────────────────────────────
    private LinearLayout chatContainer;
    private ScrollView   chatScrollView;
    private TextView     textInitialPrompt;

    // ── Input box ─────────────────────────────────────────────────────────────
    private EditText     inputArgument;
    private LinearLayout btnSend;

    // ── Bottom buttons ────────────────────────────────────────────────────────
    private LinearLayout btnExit, btnForfeit;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseFirestore    db;
    private FirebaseAuth         mAuth;
    private ListenerRegistration argumentsListener;

    // ── State ─────────────────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private String         roomId;       // the debate_rooms document ID
    private boolean        isPractice;
    private String         debateTopic;
    private String         myUid;
    private StringBuilder  chatHistory = new StringBuilder();
    private boolean        hasDebateStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debate_room);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }
        myUid = user.getUid();

        // ── Bind views ────────────────────────────────────────────────────────
        textTimer             = findViewById(R.id.textTimer);
        textParticipantName   = findViewById(R.id.textParticipantName);
        textParticipantCourse = findViewById(R.id.textParticipantCourse);
        textOnlineStatus      = findViewById(R.id.textOnlineStatus);
        textDebateTopic       = findViewById(R.id.textDebateTopic);
        inputArgument         = findViewById(R.id.inputArgument);
        btnSend               = findViewById(R.id.btnSend);
        btnExit               = findViewById(R.id.btnExit);
        btnForfeit            = findViewById(R.id.btnForfeit);
        chatContainer         = findViewById(R.id.chatContainer);
        chatScrollView        = findViewById(R.id.chatScrollView);
        textInitialPrompt     = findViewById(R.id.textInitialPrompt);

        // ── Extras ────────────────────────────────────────────────────────────
        // Accept both "roomId" (from matchmaking) and legacy "matchId" (from practice)
        roomId     = getIntent().getStringExtra("roomId");
        if (roomId == null) roomId = getIntent().getStringExtra("matchId");

        isPractice = getIntent().getBooleanExtra("isPractice", false);
        debateTopic = getIntent().getStringExtra("topic"); // may be null; loaded from Firestore below

        // ── READ-ONLY HISTORY MODE ────────────────────────────────────────────
        boolean isReadOnly = getIntent().getBooleanExtra("isReadOnly", false);
        if (isReadOnly) {
            View layoutInputBox = findViewById(R.id.layoutInputBox);
            View layoutBottomControls = findViewById(R.id.layoutBottomControls);
            
            if (layoutInputBox != null) layoutInputBox.setVisibility(android.view.View.GONE);
            if (layoutBottomControls != null) layoutBottomControls.setVisibility(android.view.View.GONE);
            textInitialPrompt.setVisibility(android.view.View.GONE);

            textTimer.setText("Ended");
            textOnlineStatus.setText("● Completed");
            textOnlineStatus.setTextColor(0xFFAAAAAA);

            if (debateTopic != null) textDebateTopic.setText(debateTopic);
            String opponentName  = getIntent().getStringExtra("opponentName");
            if (opponentName != null) textParticipantName.setText(opponentName);

            String pastHistory = getIntent().getStringExtra("history");
            if (pastHistory != null && !pastHistory.isEmpty()) {
                parseHistoryIntoBubbles(pastHistory);
            } else {
                addChatBubble("No chat history was recorded for this debate.", false);
            }
            return; // Skip standard active flow entirely
        }

        // Practice mode: show AI status badge
        if (isPractice) {
            textOnlineStatus.setText("● AI Mode");
            textOnlineStatus.setTextColor(0xFF60CC8B);
            if (debateTopic != null) textDebateTopic.setText(debateTopic);
            String opponentName  = getIntent().getStringExtra("opponentName");
            if (opponentName != null) textParticipantName.setText(opponentName);
            // Timer starts when first argument is sent
        } else {
            // ── Load room data from Firestore ─────────────────────────────────
            loadRoomData();
        }

        // ── Keyboard / Scroll Listener ────────────────────────────────────────
        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            boolean isKeyboardShowing = keypadHeight > screenHeight * 0.15;

            View cardTopic = findViewById(R.id.cardTopic);
            View bottomControls = findViewById(R.id.layoutBottomControls);

            if (isKeyboardShowing) {
                if (cardTopic != null && cardTopic.getVisibility() == View.VISIBLE) {
                    cardTopic.setVisibility(View.GONE);
                    if (bottomControls != null) bottomControls.setVisibility(View.GONE);
                    chatScrollView.postDelayed(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN), 100);
                }
            } else {
                if (cardTopic != null && cardTopic.getVisibility() == View.GONE) {
                    cardTopic.setVisibility(View.VISIBLE);
                    if (bottomControls != null) bottomControls.setVisibility(View.VISIBLE);
                    chatScrollView.postDelayed(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN), 100);
                }
            }
        });

        chatScrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom != oldBottom) {
                chatScrollView.postDelayed(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN), 100);
            }
        });

        // ── Button listeners ──────────────────────────────────────────────────
        btnSend.setOnClickListener(v -> sendArgument());

        btnExit.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Exit Debate")
                        .setMessage("Are you sure you want to exit?")
                        .setPositiveButton("Exit", (dialog, which) -> endDebate(false))
                        .setNegativeButton("Cancel", null)
                        .show());

        btnForfeit.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Forfeit Debate")
                        .setMessage("Forfeiting will count as a loss. Continue?")
                        .setPositiveButton("Forfeit", (dialog, which) -> endDebate(true))
                        .setNegativeButton("Cancel", null)
                        .show());
    }

    // ── LOAD ROOM FROM FIRESTORE ──────────────────────────────────────────────

    private void loadRoomData() {
        if (roomId == null) {
            Toast.makeText(this, "Room not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("debates").document(roomId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Room not found.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Topic
                    debateTopic = doc.getString("topic");
                    if (debateTopic != null) textDebateTopic.setText(debateTopic);

                    // Opponent name — whoever is NOT us
                    String user1Id   = doc.getString("user1");
                    String user1Name = doc.getString("user1Name");
                    String user2Name = doc.getString("user2Name");

                    String opponentName = myUid.equals(user1Id) ? user2Name : user1Name;
                    if (opponentName != null) textParticipantName.setText(opponentName);

                    textOnlineStatus.setText("● Online");
                    textOnlineStatus.setTextColor(0xFF60CC8B);

                    // Listen for incoming arguments from opponent
                    listenForArguments();
                    listenForRoomUpdates();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load room.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private com.google.firebase.firestore.ListenerRegistration roomListener;

    private void listenForRoomUpdates() {
        if (roomId == null || isPractice) return;
        
        roomListener = db.collection("debates").document(roomId)
            .addSnapshotListener((doc, e) -> {
                if (e != null || doc == null || !doc.exists()) return;
                
                String status = doc.getString("status");
                if ("ended".equals(status)) {
                    if (!isFinishing() && !isDestroyed()) {
                        navigateToResult();
                    }
                }
            });
    }

    // ── LISTEN FOR OPPONENT ARGUMENTS ─────────────────────────────────────────

    private void listenForArguments() {
        if (roomId == null) return;

        argumentsListener = db.collection("debates")
                .document(roomId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    for (var change : snapshots.getDocumentChanges()) {
                        if (change.getType() ==
                                com.google.firebase.firestore.DocumentChange.Type.ADDED) {

                            String uid  = change.getDocument().getString("uid");
                            String text = change.getDocument().getString("text");
                            if (text == null) continue;

                            boolean isMe = myUid.equals(uid);
                            // Only show opponent's messages here; our own are shown in sendArgument()
                            if (!isMe) {
                                startDebateIfNeeded();
                                addChatBubble(text, false);
                            }
                        }
                    }
                });
    }

    // ── TIMER ─────────────────────────────────────────────────────────────────

    private void startTimer(long durationMs) {
        countDownTimer = new CountDownTimer(durationMs, 1000) {
            @Override public void onTick(long ms) {
                long min = (ms / 1000) / 60;
                long sec = (ms / 1000) % 60;
                textTimer.setText(String.format("%d:%02d", min, sec));
            }
            @Override public void onFinish() {
                textTimer.setText("0:00");
                Toast.makeText(DebateRoomActivity.this,
                        "Time's up! Debate ended.", Toast.LENGTH_SHORT).show();
                endDebate(false);
            }
        }.start();
    }

    private void startDebateIfNeeded() {
        if (!hasDebateStarted) {
            hasDebateStarted = true;
            textInitialPrompt.setVisibility(android.view.View.GONE);
            startTimer(3 * 60 * 1000);
        }
    }

    // ── SEND ARGUMENT ─────────────────────────────────────────────────────────

    private void sendArgument() {
        String argument = inputArgument.getText().toString().trim();
        if (TextUtils.isEmpty(argument)) {
            inputArgument.setError("Please type your argument first");
            return;
        }

        startDebateIfNeeded();

        // Show our own bubble immediately
        addChatBubble(argument, true);
        inputArgument.setText("");

        if (isPractice) {
            getAIResponse(argument);
        } else {
            // Save to debate_rooms/{roomId}/arguments
            if (roomId == null) return;

            Map<String, Object> data = new HashMap<>();
            data.put("uid",       myUid);
            data.put("text",      argument);
            data.put("timestamp", FieldValue.serverTimestamp());

            db.collection("debates")
                    .document(roomId)
                    .collection("messages")
                    .add(data)
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to send: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        }
    }

    // ── AI RESPONSE (PRACTICE MODE) ───────────────────────────────────────────

    private void getAIResponse(String userArgument) {
        addChatBubble("Hagit AI is thinking...", false);

        new Thread(() -> {
            String aiReply = fetchAIReply(userArgument);
            if (aiReply == null || aiReply.isEmpty()) {
                aiReply = "That's an interesting point. However, I believe the evidence "
                        + "suggests otherwise. Can you provide more supporting details?";
            }
            final String finalReply = aiReply;
            runOnUiThread(() -> {
                if (chatContainer.getChildCount() > 0)
                    chatContainer.removeViewAt(chatContainer.getChildCount() - 1);
                addChatBubble(finalReply, false);
            });
        }).start();
    }

    private String fetchAIReply(String userArgument) {
        try {
            String apiKey = ApiKeyLoader.getApiKey(DebateRoomActivity.this);
            if (apiKey == null || apiKey.isEmpty()) return null;

            String apiUrl = "https://api.groq.com/openai/v1/chat/completions";

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            String safeArgument = userArgument.replace("\"", "'");
            String safeTopic    = debateTopic != null
                    ? debateTopic.replace("\"", "'") : "the debate topic";

            String prompt = "You are a debate opponent. Respond with strong logical arguments. Keep responses short and relevant. "
                    + "The debate topic is: \"" + safeTopic + "\". "
                    + "The user just argued: \"" + safeArgument + "\". "
                    + "Respond with a strong counter-argument in 2-3 sentences.";

            org.json.JSONObject messageObj = new org.json.JSONObject();
            messageObj.put("role", "user");
            messageObj.put("content", prompt);

            org.json.JSONArray messagesArr = new org.json.JSONArray();
            messagesArr.put(messageObj);

            org.json.JSONObject reqBody = new org.json.JSONObject();
            reqBody.put("model", "llama-3.1-8b-instant");
            reqBody.put("messages", messagesArr);

            byte[] bodyBytes = reqBody.toString().getBytes("UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            return extractText(response.toString());

        } catch (Exception e) {
            return null;
        }
    }

    private String extractText(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (Exception e) {
            return null;
        }
    }

    // ── CHAT BUBBLE ───────────────────────────────────────────────────────────

    private void addChatBubble(String message, boolean isUser) {
        String role = isUser ? "User" : "Opponent";
        if (!message.contains("thinking..."))
            chatHistory.append(role).append(" Argument:\n").append(message).append("\n\n");

        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(14f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin    = 10;
        params.bottomMargin = 4;
        params.setMarginStart(isUser ? 100 : 0);
        params.setMarginEnd (isUser ? 0  : 100);
        params.gravity = isUser ? Gravity.END : Gravity.START;

        bubble.setBackground(isUser
                ? getDrawable(R.drawable.bg_chat_user)
                : getDrawable(R.drawable.bg_chat_ai)
        );

        bubble.setLayoutParams(params);
        chatContainer.addView(bubble);
        chatScrollView.postDelayed(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN), 100);
    }

    private void parseHistoryIntoBubbles(String history) {
        String[] blocks = history.split("\n\n");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
            
            boolean isUserPlayer = block.startsWith("User Argument:");
            String messageStr = block.replaceFirst("^(User|Opponent|AI) Argument:\n?", "").trim();
            if (!messageStr.isEmpty()) {
                addChatBubble(messageStr, isUserPlayer);
            }
        }
    }

    // ── END DEBATE ────────────────────────────────────────────────────────────

    private void endDebate(boolean forfeited) {
        if (countDownTimer != null) countDownTimer.cancel();

        if (!isPractice && roomId != null) {
            Map<String, Object> update = new HashMap<>();
            update.put("status",    "ended");
            update.put("forfeited", forfeited);
            db.collection("debates").document(roomId)
                    .update(update)
                    .addOnCompleteListener(task -> navigateToResult());
        } else {
            navigateToResult();
        }
    }

    private boolean isNavigating = false;
    private void navigateToResult() {
        if (isNavigating) return;
        isNavigating = true;

        if (countDownTimer != null) countDownTimer.cancel();

        Intent intent = new Intent(DebateRoomActivity.this, DebateResultActivity.class);
        intent.putExtra("roomId",  roomId);
        intent.putExtra("topic",   debateTopic);
        intent.putExtra("history", chatHistory.toString());
        startActivity(intent);
        finish();
    }

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (argumentsListener != null) argumentsListener.remove();
        if (roomListener != null) roomListener.remove();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}