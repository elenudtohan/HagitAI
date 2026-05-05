package com.example.hagitai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class RecentHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList = new ArrayList<>();
    private List<HistoryItem> fullHistoryList = new ArrayList<>();

    private TextView chipAll, chipWon, chipLost;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_history);
        
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        tvSubtitle = findViewById(R.id.tvSubtitle);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            if (name == null || name.isEmpty()) name = user.getEmail();
            tvSubtitle.setText(name + " · History");
        }

        recyclerView = findViewById(R.id.rvHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);

        setupChips();
        loadHistory();
    }

    private void setupChips() {
        chipAll  = findViewById(R.id.chipAll);
        chipWon  = findViewById(R.id.chipWon);
        chipLost = findViewById(R.id.chipLost);

        View.OnClickListener listener = v -> {
            chipAll.setBackgroundResource(R.drawable.bg_chip_inactive);
            chipWon.setBackgroundResource(R.drawable.bg_chip_inactive);
            chipLost.setBackgroundResource(R.drawable.bg_chip_inactive);
            chipAll.setTextColor(0xFF6B8A9A);
            chipWon.setTextColor(0xFF6B8A9A);
            chipLost.setTextColor(0xFF6B8A9A);

            TextView clicked = (TextView) v;
            clicked.setBackgroundResource(R.drawable.bg_chip_active);
            clicked.setTextColor(0xFFE1F5EE);

            if (v == chipAll) applyFilter("All");
            else if (v == chipWon) applyFilter("Won");
            else applyFilter("Lost");
        };

        chipAll.setOnClickListener(listener);
        chipWon.setOnClickListener(listener);
        chipLost.setOnClickListener(listener);
    }

    private void applyFilter(String filter) {
        historyList.clear();
        for (HistoryItem item : fullHistoryList) {
            if (filter.equals("All")) historyList.add(item);
            else if (filter.equals("Won") && "Win".equalsIgnoreCase(item.result)) historyList.add(item);
            else if (filter.equals("Lost") && "Loss".equalsIgnoreCase(item.result)) historyList.add(item);
        }
        adapter.notifyDataSetChanged();
    }

    private void loadHistory() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("debate_history")
            .whereEqualTo("uid", user.getUid())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                fullHistoryList.clear();
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    HistoryItem item = new HistoryItem();
                    item.topic = doc.getString("topic");
                    item.opponent = doc.getString("opponent");
                    item.result = doc.getString("result");
                    item.date = doc.getString("date");
                    Long ts = doc.getLong("timestamp");
                    item.timestamp = (ts != null) ? ts : 0L;
                    item.chatHistory = doc.getString("chatHistory");
                    fullHistoryList.add(item);
                }

                // Sort client-side to avoid needing a Firestore composite index
                fullHistoryList.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

                tvSubtitle.setText(tvSubtitle.getText().toString().split("·")[0] + " · " + fullHistoryList.size() + " debates");
                applyFilter("All");
            })
            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show());
    }

    private static class HistoryItem {
        String topic, opponent, result, date, chatHistory;
        long timestamp;
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<HistoryItem> items;

        public HistoryAdapter(List<HistoryItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            holder.tvTopic.setText(item.topic != null ? item.topic : "Unknown Topic");
            holder.tvDetails.setText("vs. " + (item.opponent != null ? item.opponent : "Opponent"));
            holder.tvDate.setText(item.date != null ? item.date : "");
            
            holder.tvResult.setText(item.result);
            if ("Win".equalsIgnoreCase(item.result)) {
                holder.tvResult.setTextColor(0xFF60CC8B); // Greenish
            } else {
                holder.tvResult.setTextColor(0xFFFF6B6B); // Reddish
            }

            holder.itemView.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(RecentHistoryActivity.this, DebateRoomActivity.class);
                intent.putExtra("isReadOnly", true);
                intent.putExtra("topic", item.topic);
                intent.putExtra("opponentName", item.opponent);
                intent.putExtra("history", item.chatHistory);
                startActivity(intent);
            });

            boolean won = "Win".equalsIgnoreCase(item.result);
            holder.ivIcon.setBackgroundResource(won ? R.drawable.bg_icon_green : R.drawable.bg_icon_red);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTopic, tvResult, tvDetails, tvDate;
            ImageView ivIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTopic = itemView.findViewById(R.id.tvTopic);
                tvResult = itemView.findViewById(R.id.tvResult);
                tvDetails = itemView.findViewById(R.id.tvDetails);
                tvDate = itemView.findViewById(R.id.tvDate);
                ivIcon = itemView.findViewById(R.id.ivIcon);
            }
        }
    }
}
