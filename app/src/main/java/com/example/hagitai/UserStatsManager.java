package com.example.hagitai;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class UserStatsManager {

    private static final String PREFS_NAME = "UserStats";
    private static final String KEY_TOTAL_DEBATES = "total_debates";
    private static final String KEY_STREAK = "streak";
    private static final String KEY_LAST_OPEN_DATE = "last_open_date";
    private static final String KEY_XP = "xp";
    private static final String KEY_BADGES = "badges";

    private SharedPreferences prefs;

    public UserStatsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void updateDailyStreak() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        String lastOpen = prefs.getString(KEY_LAST_OPEN_DATE, "");

        SharedPreferences.Editor editor = prefs.edit();

        if (!lastOpen.equals(today)) {
            if (isYesterday(lastOpen)) {
                // Continue streak
                int newStreak = getStreak() + 1;
                editor.putInt(KEY_STREAK, newStreak);
                addXP(50); // reward XP for daily streak
            } else {
                // Reset streak if missed a day
                editor.putInt(KEY_STREAK, 1);
            }
            editor.putString(KEY_LAST_OPEN_DATE, today);
            editor.apply();
        }
    }

    private boolean isYesterday(String lastOpen) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            Calendar last = Calendar.getInstance();
            Calendar now = Calendar.getInstance();
            last.setTime(sdf.parse(lastOpen));
            last.add(Calendar.DATE, 1);
            return sdf.format(last.getTime()).equals(sdf.format(now.getTime()));
        } catch (Exception e) {
            return false;
        }
    }

    public void addDebate() {
        int newTotal = getTotalDebates() + 1;
        prefs.edit().putInt(KEY_TOTAL_DEBATES, newTotal).apply();
        addXP(100); // each debate gives XP
        checkForBadges(newTotal);
    }

    private void checkForBadges(int totalDebates) {
        int badges = getBadges();
        if (totalDebates >= 10 && badges < 1) badges = 1;
        if (totalDebates >= 25 && badges < 2) badges = 2;
        if (totalDebates >= 50 && badges < 3) badges = 3;
        prefs.edit().putInt(KEY_BADGES, badges).apply();
    }

    public void addXP(int amount) {
        int xp = getXP() + amount;
        prefs.edit().putInt(KEY_XP, xp).apply();
    }

    // Getters
    public int getTotalDebates() { return prefs.getInt(KEY_TOTAL_DEBATES, 0); }
    public int getStreak() { return prefs.getInt(KEY_STREAK, 0); }
    public int getXP() { return prefs.getInt(KEY_XP, 0); }
    public int getBadges() { return prefs.getInt(KEY_BADGES, 0); }
}

