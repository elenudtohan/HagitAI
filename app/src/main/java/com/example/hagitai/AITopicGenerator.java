package com.example.hagitai;

import android.content.Context;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
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

public class AITopicGenerator {

    private static final String TAG            = "AITopicGenerator";

    private static final String AI_URL_BASE = "https://api.groq.com/openai/v1/chat/completions";
    
    private static final String[] FALLBACK_TOPICS = {
        "Are social media platforms more harmful than beneficial?",
        "Should homework be banned in schools?",
        "Is artificial intelligence a threat to humanity?",
        "Should voting be made mandatory?",
        "Does technology isolate us more than it connects us?",
        "Should university education be totally free?",
        "Is space exploration worth the massive cost?",
        "Should a four-day work week be standard?",
        "Are single-sex schools more effective than co-ed schools?",
        "Should the drinking age be lowered?"
    };

    private static String getRandomFallback() {
        return FALLBACK_TOPICS[new java.util.Random().nextInt(FALLBACK_TOPICS.length)];
    }

    // ── Callback interface ─────────────────────────────
    public interface TopicCallback {
        void onTopicGenerated(String topic);
        void onError(String errorMessage);
    }

    // ── Daily topic (checks Firestore cache first) ─────
    public static void generateDailyTopic(Context context, TopicCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        db.collection("topics").document(dateKey).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String cached = doc.getString("topic");
                        if (!TextUtils.isEmpty(cached)) {
                            deliverOnMainThread(callback, cached);
                            return;
                        }
                    }
                    generateFromGemini(context, db, dateKey, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore read failed", e);
                    generateFromGemini(context, db, dateKey, callback);
                });
    }

    // ── Fresh topic (always calls Gemini, no caching) ──
    public static void generateFreshTopic(Context context, TopicCallback callback) {
        generateFromGemini(context, null, null, callback);
    }

    // ── Core Gemini call ───────────────────────────────
    private static void generateFromGemini(Context context,
                                           FirebaseFirestore db,
                                           String dateKey,
                                           TopicCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String apiKey = ApiKeyLoader.getApiKey(context);
                if (apiKey == null || apiKey.isEmpty()) {
                    deliverErrorOnMainThread(callback, "OpenAI API Key is missing.");
                    return;
                }

                URL url = new URL(AI_URL_BASE);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                // ── Build JSON body safely ───────────────────
                String prompt = "Generate EXACTLY ONE short, clear, and debatable topic. " 
                              + "Return ONLY the topic text itself. Do NOT include phrases like 'Here is a topic', "
                              + "do NOT give multiple options, and avoid bullet points. "
                              + "Make sure it is unique and creative. Random seed: " + Math.random();

                JSONObject messageObj = new JSONObject();
                messageObj.put("role", "user");
                messageObj.put("content", prompt);

                JSONArray messagesArr = new JSONArray();
                messagesArr.put(messageObj);

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "llama-3.1-8b-instant");
                requestBody.put("messages", messagesArr);

                byte[] bodyBytes = requestBody.toString().getBytes("UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                    os.flush();
                }

                // ── Check HTTP response code ─────────────────
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    BufferedReader errReader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream()));
                    StringBuilder errBody = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) errBody.append(errLine);
                    errReader.close();

                    Log.e(TAG, "Gemini error " + responseCode + ": " + errBody.toString());
                    
                    String fallbackTopic = getRandomFallback();
                    if (db != null && !TextUtils.isEmpty(dateKey)) {
                        HashMap<String, Object> data = new HashMap<>();
                        data.put("topic", fallbackTopic);
                        db.collection("topics").document(dateKey).set(data);
                    }
                    deliverOnMainThread(callback, fallbackTopic);
                    return;
                }

                // ── Read success response ────────────────────
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                String topic = extractTopic(response.toString());
                if (TextUtils.isEmpty(topic)) {
                    topic = getRandomFallback();
                }

                final String finalTopic = topic;

                // ── Cache in Firestore if this is a daily topic ──
                if (db != null && !TextUtils.isEmpty(dateKey)) {
                    HashMap<String, Object> data = new HashMap<>();
                    data.put("topic", finalTopic);
                    db.collection("topics").document(dateKey).set(data)
                            .addOnSuccessListener(v ->
                                    Log.d(TAG, "Topic saved to Firestore: " + finalTopic))
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Firestore save failed", e));
                }

                deliverOnMainThread(callback, finalTopic);

            } catch (Exception e) {
                Log.e(TAG, "Gemini request failed", e);
                deliverErrorOnMainThread(callback, "Network error: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Parse AI JSON response ─────────────────────
    private static String extractTopic(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            return TextUtils.isEmpty(text) ? null : text.trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse AI response: " + json, e);
            return null;
        }
    }

    // ── Deliver results on the main (UI) thread ────────
    private static void deliverOnMainThread(TopicCallback callback, String topic) {
        new Handler(Looper.getMainLooper()).post(() ->
                callback.onTopicGenerated(topic));
    }

    private static void deliverErrorOnMainThread(TopicCallback callback, String error) {
        new Handler(Looper.getMainLooper()).post(() ->
                callback.onError(error));
    }
}