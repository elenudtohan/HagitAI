// UserData.java (Demo only)
package com.example.hagitai;

public class UserData {
    public static String name = "Juan dela Cruz";
    public static int xp = 2340;
    public static int level = 12;
    public static String rank = "Gold";
    public static int wins = 34;
    public static int debates = 48;

    // 🔥 DEMO TOPIC
    public static String demoTopic = "Should professional athletes be considered role models?";

    public static int getWinRate() {
        return (wins * 100) / debates;
    }
}