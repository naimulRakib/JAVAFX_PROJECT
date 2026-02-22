package com.scholar.service;

import com.scholar.service.AuthService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Service; // 🟢 নতুন
import javax.sql.DataSource; // 🟢 নতুন

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

@Service // 🌟 ১. ক্লাসটিকে স্প্রিং সার্ভিস হিসেবে রেজিস্টার করা হলো
public class ECAService {

    @Autowired
    private DataSource dataSource; // 🟢 ২. স্প্রিং বুট অটোমেটিক কানেকশন পুল ইনজেক্ট করবে

    // ৩. কানেকশন নেওয়ার জন্য হেল্পার মেথড
    private Connection connect() throws Exception {
        return dataSource.getConnection();
    }

    // ==========================================
    // 1. LINK ACCOUNTS (Logic Unchanged)
    // ==========================================
    public boolean linkAccounts(String lcUser, String dpUser) {
        if (AuthService.CURRENT_USER_ID == null) return false;

        String sql = """
            INSERT INTO user_integrations (user_id, leetcode_username, devpost_username)
            VALUES (?::uuid, ?, ?)
            ON CONFLICT (user_id) 
            DO UPDATE SET leetcode_username = EXCLUDED.leetcode_username, 
                          devpost_username = EXCLUDED.devpost_username;
        """;
        try (Connection conn = connect(); // 🟢 Updated
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setString(2, lcUser);
            pstmt.setString(3, dpUser);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ==========================================
    // 2. GET LINKED ACCOUNTS (Logic Unchanged)
    // ==========================================
    public String[] getLinkedAccounts() {
        if (AuthService.CURRENT_USER_ID == null) return null;
        
        String sql = "SELECT leetcode_username, devpost_username FROM user_integrations WHERE user_id = ?::uuid";
        try (Connection conn = connect(); // 🟢 Updated
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

    // ==========================================
    // 3. FETCH LEETCODE STATS (Live GraphQL - Unchanged)
    // ==========================================
    public int[] fetchLeetCodeStats(String username) {
        try {
            String query = "{\"query\": \"{ matchedUser(username: \\\"" + username + "\\\") { submitStats: submitStatsGlobal { acSubmissionNum { difficulty count } } } }\"}";
            URL url = new URL("https://leetcode.com/graphql");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) { os.write(query.getBytes()); }
            Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            JSONObject json = new JSONObject(result);
            if (json.has("errors")) return null;
            JSONArray stats = json.getJSONObject("data").getJSONObject("matchedUser").getJSONObject("submitStats").getJSONArray("acSubmissionNum");
            return new int[]{ stats.getJSONObject(0).getInt("count"), stats.getJSONObject(1).getInt("count"), stats.getJSONObject(2).getInt("count"), stats.getJSONObject(3).getInt("count") };
        } catch (Exception e) { return null; }
    }

    // ==========================================
    // 4. FETCH DEVPOST STATS (Scraping - Unchanged)
    // ==========================================
    public int fetchDevpostProjectCount(String username) {
        try {
            String url = "https://devpost.com/" + username;
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            Elements projects = doc.select(".gallery-item");
            return projects.size();
        } catch (Exception e) { return -1; }
    }

    // ==========================================
    // GOAL SETTING (Logic Unchanged)
    // ==========================================
    public boolean setCodingGoal(String type, int tEasy, int tMed, int tHard, int cEasy, int cMed, int cHard) {
        String dSql = "UPDATE coding_goals SET is_active = FALSE WHERE user_id = ?::uuid AND goal_type = ?";
        String iSql = "INSERT INTO coding_goals (user_id, goal_type, target_easy, target_medium, target_hard, start_easy, start_medium, start_hard, end_date) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, NOW() + INTERVAL '7 days')";

        try (Connection conn = connect()) { // 🟢 Updated
            try (PreparedStatement p = conn.prepareStatement(dSql)) { p.setObject(1, AuthService.CURRENT_USER_ID); p.setString(2, type); p.executeUpdate(); }
            try (PreparedStatement p = conn.prepareStatement(iSql)) {
                p.setObject(1, AuthService.CURRENT_USER_ID); p.setString(2, type); p.setInt(3, tEasy); p.setInt(4, tMed); p.setInt(5, tHard); p.setInt(6, cEasy); p.setInt(7, cMed); p.setInt(8, cHard);
                return p.executeUpdate() > 0;
            }
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public ResultSet getActiveGoal() {
        String sql = "SELECT * FROM coding_goals WHERE user_id = ?::uuid AND is_active = TRUE ORDER BY created_at DESC LIMIT 1";
        try {
            Connection conn = connect(); // 🟢 Updated
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            return pstmt.executeQuery();
        } catch (Exception e) { return null; }
    }
    
    public ResultSet getGoalHistory() {
        String sql = "SELECT * FROM coding_goals WHERE user_id = ?::uuid AND is_active = FALSE ORDER BY created_at DESC";
        try {
            Connection conn = connect(); // 🟢 Updated
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            return pstmt.executeQuery();
        } catch (Exception e) { return null; }
    }

    // ==========================================
    // 5. FETCH TOPIC STRENGTH (GraphQL - Unchanged)
    // ==========================================
    public java.util.Map<String, Integer> fetchTopicStats(String username) {
        java.util.Map<String, Integer> topicMap = new java.util.HashMap<>();
        try {
            String query = "{\"query\": \"{ matchedUser(username: \\\"" + username + "\\\") { tagProblemCounts { advanced { tagName problemsSolved } intermediate { tagName problemsSolved } fundamental { tagName problemsSolved } } } }\"}";
            URL url = new URL("https://leetcode.com/graphql");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) { os.write(query.getBytes()); }
            Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            JSONObject json = new JSONObject(result);
            if (!json.has("data") || json.isNull("data")) return null;
            JSONObject tags = json.getJSONObject("data").getJSONObject("matchedUser").getJSONObject("tagProblemCounts");
            extractTags(tags.getJSONArray("fundamental"), topicMap);
            extractTags(tags.getJSONArray("intermediate"), topicMap);
            extractTags(tags.getJSONArray("advanced"), topicMap);
            return topicMap;
        } catch (Exception e) { return null; }
    }

    private void extractTags(JSONArray arr, java.util.Map<String, Integer> map) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            map.put(obj.getString("tagName"), obj.getInt("problemsSolved"));
        }
    }
}