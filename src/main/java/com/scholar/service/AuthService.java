package com.scholar.service;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Service; // 🟢 নতুন
import javax.sql.DataSource; // 🟢 নতুন

@Service
public class AuthService {

    @Autowired
    private DataSource dataSource;


  

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
public static String CURRENT_USER_NAME= null;

// JDBC Connect logic updated to use Spring's DataSource
    private Connection connect() throws SQLException {
        return dataSource.getConnection();
    }



    // Helper to clear session on Logout
  




    // AuthService.java এর login মেথডটি এভাবে আপডেট করুন
public boolean login(String email, String password) {
    // 🌟 পরিবর্তন: id এর সাথে username ও সিলেক্ট করুন
    String sql = "SELECT id, username FROM users WHERE email = ? AND password = ?";
    
    try (Connection conn = connect();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setString(1, email);
        pstmt.setString(2, password);
        ResultSet rs = pstmt.executeQuery();

        if (rs.next()) {
            // আইডি এবং নাম দুটোই সেভ করুন
            CURRENT_USER_ID = (UUID) rs.getObject("id");
            CURRENT_USER_NAME = rs.getString("username"); // 🌟 এখন নাম আর null থাকবে না
            CURRENT_USER_EMAIL = email;
            
            System.out.println("✅ Identity Verified: " + CURRENT_USER_NAME);
            
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
        SELECT cm.channel_id, cm.role, cm.status, c.unique_code, c.name 
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

    // 🌟 DriverManager-এর বদলে এখন আমরা Spring-এর dataSource ব্যবহার করব
    try (Connection conn = dataSource.getConnection()) {
        
        // আপনার বিদ্যমান loadChannelContext মেথডটি কল করে সেশন আপডেট করা (Unchanged)
        loadChannelContext(conn);
        
        System.out.println("🔄 Session Refreshed for: " + CURRENT_USER_ID);
    } catch (SQLException e) {
        System.err.println("❌ Session refresh failed: " + e.getMessage());
    }
}

// AuthService.java এর ভেতরে



    // ২. নাম বের করার মেথড
    public String getUsername(String email) {
        String username = "Unknown"; // ডিফল্ট ভ্যালু
        String sql = "SELECT username FROM users WHERE email = ?";

      try (Connection conn = connect(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                username = rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return username;
    }


}