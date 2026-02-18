package com.scholar.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class ECAService {

    // 1. LINK ACCOUNTS (Save Usernames to DB)
    public boolean linkAccounts(String lcUser, String dpUser) {
        if (AuthService.CURRENT_USER_ID == null) return false;

        String sql = """
            INSERT INTO user_integrations (user_id, leetcode_username, devpost_username)
            VALUES (?::uuid, ?, ?)
            ON CONFLICT (user_id) 
            DO UPDATE SET leetcode_username = EXCLUDED.leetcode_username, 
                          devpost_username = EXCLUDED.devpost_username;
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setString(2, lcUser);
            pstmt.setString(3, dpUser);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // 2. GET LINKED ACCOUNTS
    public String[] getLinkedAccounts() {
        if (AuthService.CURRENT_USER_ID == null) return null;
        
        String sql = "SELECT leetcode_username, devpost_username FROM user_integrations WHERE user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                    rs.getString("leetcode_username"), 
                    rs.getString("devpost_username")
                };
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 3. FETCH LEETCODE STATS (Live GraphQL)
    public int[] fetchLeetCodeStats(String username) {
        try {
            // GraphQL Query for Solved Problems
            String query = "{\"query\": \"{ matchedUser(username: \\\"" + username + "\\\") { submitStats: submitStatsGlobal { acSubmissionNum { difficulty count } } } }\"}";
            
            URL url = new URL("https://leetcode.com/graphql");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(query.getBytes());
            }

            Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";

            // Parse JSON
            JSONObject json = new JSONObject(result);
            if (json.has("errors")) return null; // Username not found

            JSONArray stats = json.getJSONObject("data")
                                  .getJSONObject("matchedUser")
                                  .getJSONObject("submitStats")
                                  .getJSONArray("acSubmissionNum");

            // Returns [Total, Easy, Medium, Hard]
            return new int[]{
                stats.getJSONObject(0).getInt("count"), // Total
                stats.getJSONObject(1).getInt("count"), // Easy
                stats.getJSONObject(2).getInt("count"), // Medium
                stats.getJSONObject(3).getInt("count")  // Hard
            };

        } catch (Exception e) {
            System.err.println("LeetCode Fetch Error: " + e.getMessage());
            return null;
        }
    }

    // 4. FETCH DEVPOST STATS (Live Scraping)
    public int fetchDevpostProjectCount(String username) {
        try {
            String url = "https://devpost.com/" + username;
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0") // Pretend to be a browser
                    .get();
            
            // Devpost lists projects in a grid. We count the items.
            Elements projects = doc.select(".gallery-item");
            return projects.size();
        } catch (Exception e) {
            System.err.println("Devpost Fetch Error: " + e.getMessage());
            return -1; // Error code
        }
    }

    // 1. SET NEW GOAL (Snapshots current stats)
    public boolean setCodingGoal(String type, int tEasy, int tMed, int tHard, int currentEasy, int currentMed, int currentHard) {
        // Deactivate old goals first
        String disableSql = "UPDATE coding_goals SET is_active = FALSE WHERE user_id = ?::uuid AND goal_type = ?";
        
        String insertSql = """
            INSERT INTO coding_goals 
            (user_id, goal_type, target_easy, target_medium, target_hard, start_easy, start_medium, start_hard, end_date)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, NOW() + INTERVAL '7 days')
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Disable old goals
            try (PreparedStatement pstmt = conn.prepareStatement(disableSql)) {
                pstmt.setObject(1, AuthService.CURRENT_USER_ID);
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }

            // Create new goal
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setObject(1, AuthService.CURRENT_USER_ID);
                pstmt.setString(2, type);
                pstmt.setInt(3, tEasy);
                pstmt.setInt(4, tMed);
                pstmt.setInt(5, tHard);
                pstmt.setInt(6, currentEasy); // Snapshot!
                pstmt.setInt(7, currentMed);
                pstmt.setInt(8, currentHard);
                return pstmt.executeUpdate() > 0;
            }
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // 2. GET ACTIVE GOAL
    public ResultSet getActiveGoal() {
        String sql = "SELECT * FROM coding_goals WHERE user_id = ?::uuid AND is_active = TRUE ORDER BY created_at DESC LIMIT 1";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            return pstmt.executeQuery();
        } catch (Exception e) { return null; }
    }
    
    // 3. GET HISTORY
    public ResultSet getGoalHistory() {
        String sql = "SELECT * FROM coding_goals WHERE user_id = ?::uuid AND is_active = FALSE ORDER BY created_at DESC";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            return pstmt.executeQuery();
        } catch (Exception e) { return null; }
    }


    // Inside ECAService.java

// 5. FETCH TOPIC STRENGTH (Pie Chart Data)
    public java.util.Map<String, Integer> fetchTopicStats(String username) {
        java.util.Map<String, Integer> topicMap = new java.util.HashMap<>();
        try {
            // 1. Define the GraphQL Query
            String query = "{\"query\": \"{ matchedUser(username: \\\"" + username + "\\\") { tagProblemCounts { advanced { tagName problemsSolved } intermediate { tagName problemsSolved } fundamental { tagName problemsSolved } } } }\"}";
            
            // 2. Open Connection
            URL url = new URL("https://leetcode.com/graphql");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // 3. Send Request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(query.getBytes());
            }

            // 4. Read Response (THIS DEFINES 'result')
            Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : ""; // ✅ Fixed: Now 'result' exists!

            // 5. Parse JSON
            JSONObject json = new JSONObject(result);
            
            // Check if user exists
            if (!json.has("data") || json.isNull("data")) return null;
            
            JSONObject matchedUser = json.getJSONObject("data").getJSONObject("matchedUser");
            JSONObject tags = matchedUser.getJSONObject("tagProblemCounts");
            
            // Helper to extract from categories
            extractTags(tags.getJSONArray("fundamental"), topicMap);
            extractTags(tags.getJSONArray("intermediate"), topicMap);
            extractTags(tags.getJSONArray("advanced"), topicMap);
            
            return topicMap;
        } catch (Exception e) { 
            e.printStackTrace(); 
            return null; 
        }
    }

    // Helper method (Keep this inside ECAService class)
    private void extractTags(JSONArray arr, java.util.Map<String, Integer> map) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            map.put(obj.getString("tagName"), obj.getInt("problemsSolved"));
        }
    }


}