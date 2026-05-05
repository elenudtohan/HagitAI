# Goal Description
Redesign the app's interface focusing on a clean, minimal, glassmorphism-inspired style without changing the color theme. Move the menu icon inside the user profile section. Replace all dummy data in the Throne Ranking feature (in HomeActivity) with real user data, implementing a level system based on wins (Level = wins, starting at 0).

## Proposed Changes

### UI & Styling
- **Drawables**: Update [bg_glass.xml](file:///c:/HagitAi/HagitAi/app/src/main/res/drawable/bg_glass.xml), [rounded_card.xml](file:///c:/HagitAi/HagitAi/app/src/main/res/drawable/rounded_card.xml), [rounded_glass_square.xml](file:///c:/HagitAi/HagitAi/app/src/main/res/drawable/rounded_glass_square.xml) to enhance the glassmorphism effect (using semi-transparent layers, subtle strokes, rounded corners).
- **[activity_home.xml](file:///c:/HagitAi/HagitAi/app/src/main/res/layout/activity_home.xml)**:
  - Restructure the top header to place the menu icon inside the user profile section container.
  - Refine padding, margins, and card backgrounds to achieve the requested clean, modern layout.
  - Assign IDs to the avatar, rank name, subtitle, and XP for the top 5 ranking rows.
- **[activity_leaderboard.xml](file:///c:/HagitAi/HagitAi/app/src/main/res/layout/activity_leaderboard.xml)**:
  - Apply the glassmorphism and minimal styling consistently to the leaderboard layout and its RecyclerView items.
- **`item_leaderboard_user.xml`**:
  - Update the leaderboard list item design to match the new minimal glassmorphism theme.

### Logic (Real User Data Integration)
- **[HomeActivity.java](file:///c:/HagitAi/HagitAi/app/src/main/java/com/example/hagitai/HomeActivity.java)**:
  - Update [loadLeaderboard()](file:///c:/HagitAi/HagitAi/app/src/main/java/com/example/hagitai/HomeActivity.java#299-321) to fetch `name`, `xp`, and `wins` from Firebase.
  - Calculate user levels based on `wins` (Level = wins) and determine the tier (Bronze, Silver, etc.) to mirror the logic in [LeaderboardActivity](file:///c:/HagitAi/HagitAi/app/src/main/java/com/example/hagitai/LeaderboardActivity.java#23-161).
  - Dynamically populate all 5 ranking slots (name, subtitle, xp/wins, rank icon/number). Hide unused rows if there are fewer than 5 users.

## Verification Plan
### Automated Tests
- Not applicable directly for UI aesthetics, but compilation checks will run successfully using `./gradlew assembleDebug`.

### Manual Verification
1. Run the app in an emulator/device.
2. Observe the Home screen header to ensure the hamburger menu icon is inside the profile section.
3. Check the "Throne Ranking" list on the Home screen to confirm it shows real users (no placeholder names if there are real users in Firebase), and that their level correctly matches their wins.
4. Open the full Throne Ranking leaderboard to ensure the new glassmorphism theme is applied correctly.
