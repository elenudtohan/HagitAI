package com.example.hagitai;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;

public class MatchFoundActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_found);

        String roomId = getIntent().getStringExtra("roomId");

        TextView tvOpponentName = findViewById(R.id.tvOpponentName);
        TextView tvDebateTopic  = findViewById(R.id.tvDebateTopic);

        if (roomId != null) {
            FirebaseFirestore.getInstance()
                    .collection("debates")
                    .document(roomId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String user1 = doc.getString("user1");
                            String user1Name = doc.getString("user1Name");
                            String user2Name = doc.getString("user2Name");
                            String topic = doc.getString("topic");

                            String myUid = "";
                            com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                            if (fUser != null) myUid = fUser.getUid();

                            String opponentName = (myUid.equals(user1)) ? user2Name : user1Name;

                            if (tvOpponentName != null) tvOpponentName.setText(opponentName != null ? opponentName : "Opponent");
                            if (tvDebateTopic != null) tvDebateTopic.setText(topic != null ? topic : "Unknown Topic");
                        }
                    });
        }

        Button startDebateButton = findViewById(R.id.startDebateButton);
        startDebateButton.setOnClickListener(v -> {
            Intent intent = new Intent(MatchFoundActivity.this, DebateRoomActivity.class);
            intent.putExtra("roomId", roomId); // ← key is "roomId", not "matchId"
            intent.putExtra("isPractice", false);
            startActivity(intent);
            finish();
        });
    }
}