package com.scholar.service;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.*;
import java.util.UUID;

public class AuthService {

    private final String DB_URL;
    private final String DB_USER;
    private final String DB_PASSWORD;

    // ==========================================
    // 🌍 GLOBAL IDENTITY (The "Session")
    // ==========================================
    public static UUID CURRENT_USER_ID = null;

    public static String CURRENT_USER_EMAIL = null;

     
    // NEW: Multiverse Context
    public static int CURRENT_CHANNEL_ID = -1;      // -1 means "No Channel" (Lobby)
    public static String CURRENT_USER_ROLE = "";    // "admin" or "student"
    public static String CURRENT_CHANNEL_CODE = ""; // e.g. "CSE24"
    public static String CURRENT_CHANNEL_NAME = "Personal Workspace";
public static String CURRENT_USER_STATUS = "";
    // Helper to clear session on Logout
  

    public AuthService() {
        try {
            Dotenv dotenv = Dotenv.load();
            this.DB_URL = dotenv.get("DB_URL");
            this.DB_USER = dotenv.get("DB_USER");
            this.DB_PASSWORD = dotenv.get("DB_PASSWORD");
        } catch (Exception e) {
            System.err.println("❌ ERROR: .env file missing in project root.");
            throw new RuntimeException("Environment configuration failed.");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // ==========================================================
    // LOGIN: Verifies User AND Loads Channel Context
    // ==========================================================
    public boolean login(String email, String password) {
        String sql = "SELECT id FROM users WHERE email = ? AND password = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // 1. Set Basic Identity
                CURRENT_USER_ID = (UUID) rs.getObject("id");
                CURRENT_USER_EMAIL = email;
                System.out.println("✅ Identity Verified: " + CURRENT_USER_ID);
                
                // 2. LOAD CHANNEL CONTEXT (The Multiverse Check)
                loadChannelContext(conn); 
                
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ Database Login Error: " + e.getMessage());
        }
        return false;
    }

    // NEW: Helper method to find which channel the user is in
   private void loadChannelContext(Connection conn) {
        CURRENT_CHANNEL_ID = -1;
        CURRENT_USER_ROLE = "guest";
        CURRENT_CHANNEL_NAME = "Personal Workspace"; // Default

        // We added "c.name" to the SELECT query
        String sql = """
            SELECT cm.channel_id, cm.role, c.unique_code, c.name 
            FROM channel_members cm
            JOIN channels c ON cm.channel_id = c.id
            WHERE cm.user_id = ? AND cm.status = 'approved'
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, CURRENT_USER_ID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                CURRENT_CHANNEL_ID = rs.getInt("channel_id");
                CURRENT_USER_ROLE = rs.getString("role");
                CURRENT_CHANNEL_CODE = rs.getString("unique_code");
                CURRENT_USER_STATUS = rs.getString("status");
                // ✅ Now we save the name!
                CURRENT_CHANNEL_NAME = rs.getString("name"); 
                
                System.out.println("🌍 Entered Channel: " + CURRENT_CHANNEL_NAME);
                System.out.println("🌍 Context Loaded: " + CURRENT_USER_ROLE + " | Status: " + CURRENT_USER_STATUS);
            }
        } catch (SQLException e) {
            System.err.println("⚠️ Context Load Error: " + e.getMessage());
        }
    }

    // ==========================================================
    // SIGNUP: Basic User Creation
    // (Note: Channel Joining happens in ChannelService, not here)
    // ==========================================================
    public boolean signup(String email, String password) {
        // Generate a new UUID for the user
        UUID newUserId = UUID.randomUUID();
        
        String sql = "INSERT INTO users (id, email, password) VALUES (?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setObject(1, newUserId); // ✅ Fixed: Using generated UUID
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Signup failed: " + e.getMessage());
            return false;
        }
    }
    // AuthService.java ফাইলের ভেতরে যোগ করুন
public static void logout() {
    CURRENT_USER_ID = null;
    CURRENT_USER_EMAIL = null;
    CURRENT_CHANNEL_ID = -1;
    CURRENT_USER_ROLE = "";
    CURRENT_USER_STATUS = "";
    CURRENT_CHANNEL_NAME = "Personal Workspace";
    System.out.println("🔒 User logged out. Session cleared.");
}



public void refreshSession() {
    if (CURRENT_USER_ID == null) return;

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
        // বিদ্যমান loadChannelContext মেথডটি কল করে সেশন আপডেট করা
        loadChannelContext(conn);
        System.out.println("🔄 Session Refreshed for: " + CURRENT_USER_ID);
    } catch (SQLException e) {
        System.err.println("❌ Session refresh failed: " + e.getMessage());
    }
}

}