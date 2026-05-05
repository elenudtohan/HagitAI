package com.example.hagitai;

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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LeaderboardAdapter adapter;
    private List<UserItem> userList = new ArrayList<>();
    
    private FirebaseFirestore db;
    private TextView tvToggleMode, btnToggleMode;
    private boolean sortByXP = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);
        
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        tvToggleMode = findViewById(R.id.tvToggleMode);
        btnToggleMode = findViewById(R.id.btnToggleMode);
        
        btnToggleMode.setOnClickListener(v -> {
            sortByXP = !sortByXP;
            tvToggleMode.setText(sortByXP ? "Sorted by XP" : "Sorted by Wins");
            btnToggleMode.setText(sortByXP ? "By Wins" : "By XP");
            loadLeaderboard();
        });

        recyclerView = findViewById(R.id.rvLeaderboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderboardAdapter(userList);
        recyclerView.setAdapter(adapter);

        loadLeaderboard();
    }

    private void loadLeaderboard() {
        String field = sortByXP ? "xp" : "wins";
        
        db.collection("users")
            .orderBy(field, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                userList.clear();
                int rank = 1;
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    UserItem item = new UserItem();
                    item.rank = rank++;
                    item.name = doc.getString("name");
                    Long xp = doc.getLong("xp");
                    Long wins = doc.getLong("wins");
                    item.xp = xp != null ? xp : 0;
                    item.wins = wins != null ? wins : 0;
                    userList.add(item);
                }
                adapter.notifyDataSetChanged();
            })
            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show());
    }

    private static class UserItem {
        int rank;
        String name;
        long xp, wins;
    }

    private class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

        private List<UserItem> items;

        public LeaderboardAdapter(List<UserItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UserItem item = items.get(position);
            
            String emoji = String.valueOf(item.rank);
            if (item.rank == 1) emoji = "👑";
            else if (item.rank == 2) emoji = "🥈";
            else if (item.rank == 3) emoji = "🥉";
            
            holder.tvRankNumber.setText(emoji);
            
            String dispName = item.name != null && !item.name.isEmpty() ? item.name : "Unknown";
            holder.tvName.setText(dispName);
            holder.tvAvatar.setText(String.valueOf(dispName.charAt(0)).toUpperCase());

            // Rank logic based on XP
            long level = (item.xp / 1000) + 1;
            String rankName;
            if (level < 5)       rankName = "Bronze";
            else if (level < 10) rankName = "Silver";
            else if (level < 15) rankName = "Gold";
            else if (level < 20) rankName = "Platinum";
            else                 rankName = "Diamond";
            
            holder.tvSubtitle.setText("Level " + level + " · " + rankName);
            
            if (sortByXP) {
                holder.tvScore.setText(item.xp + " XP");
                holder.tvScore.setTextColor(0xFFFFD700); // Gold
            } else {
                holder.tvScore.setText(item.wins + " Wins");
                holder.tvScore.setTextColor(0xFF60CC8B); // Greenish
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRankNumber, tvAvatar, tvName, tvSubtitle, tvScore;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRankNumber = itemView.findViewById(R.id.tvRankNumber);
                tvAvatar = itemView.findViewById(R.id.tvAvatar);
                tvName = itemView.findViewById(R.id.tvName);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
                tvScore = itemView.findViewById(R.id.tvScore);
            }
        }
    }
}
