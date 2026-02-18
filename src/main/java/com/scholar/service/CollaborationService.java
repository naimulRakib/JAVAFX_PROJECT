package com.scholar.service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// If DatabaseConnection is in the same package (com.scholar.service), no import is needed.
// If it is in com.scholar, uncomment the line below:
// import com.scholar.DatabaseConnection;

public class CollaborationService {

    // --- Data Models ---
    public record Channel(int id, String title, String desc) {}
    public record Post(int id, String title, String status, String meetLink, String ownerId) {}
    public record Message(String sender, String content, String time) {}

    // 1. Create a Channel (e.g., "Hackathons 2026")
    public boolean createChannel(String title, String desc) {
        String sql = "INSERT INTO channels (title, description, created_by) VALUES (?, ?, ?)";
        // Use the singleton connection
        Connection conn = DatabaseConnection.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, desc);
            pstmt.setObject(3, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    // 2. Create a Team Post (Automatically makes you the OWNER)
    public boolean createPost(int channelId, String title) {
        String sqlPost = "INSERT INTO posts (channel_id, title, created_by, status) VALUES (?, ?, ?, 'OPEN') RETURNING id";
        String sqlMember = "INSERT INTO team_members (post_id, user_id, role, status) VALUES (?, ?, 'OWNER', 'APPROVED')";
        
        Connection conn = DatabaseConnection.getConnection();
        try {
            // Save state to restore later if needed (though we are using a shared connection)
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); // Start Transaction

            int postId = -1;
            
            try (PreparedStatement p1 = conn.prepareStatement(sqlPost)) {
                p1.setInt(1, channelId);
                p1.setString(2, title);
                p1.setObject(3, AuthService.CURRENT_USER_ID);
                ResultSet rs = p1.executeQuery();
                if (rs.next()) postId = rs.getInt(1);
            }

            if (postId != -1) {
                try (PreparedStatement p2 = conn.prepareStatement(sqlMember)) {
                    p2.setInt(1, postId);
                    p2.setObject(2, AuthService.CURRENT_USER_ID);
                    p2.executeUpdate();
                }
                conn.commit();
                conn.setAutoCommit(originalAutoCommit); // Reset
                return true;
            } else {
                conn.rollback();
                conn.setAutoCommit(originalAutoCommit); // Reset
                return false;
            }
        } catch (SQLException e) { 
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return false; 
        }
    }

    // 3. Check my status (Are you Owner, Member, or Pending?)
    public String getMyStatus(int postId) {
        String sql = "SELECT status FROM team_members WHERE post_id = ? AND user_id = ?";
        Connection conn = DatabaseConnection.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("status");
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
        return "NONE";
    }

    // 4. Apply to Join
    public boolean applyToTeam(int postId) {
        String sql = "INSERT INTO team_members (post_id, user_id, status) VALUES (?, ?, 'PENDING')";
        Connection conn = DatabaseConnection.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            return false; 
        }
    }

    // 5. Send Chat Message
    public boolean sendMessage(int postId, String message) {
        String sql = "INSERT INTO messages (post_id, user_id, content) VALUES (?, ?, ?)";
        Connection conn = DatabaseConnection.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            pstmt.setString(3, message);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            return false; 
        }
    }

    // 6. Fetch Chat Messages (Fixed & Integrated)
    public List<Message> getMessages(int postId) {
        List<Message> list = new ArrayList<>();
        // Simple query to get email (sender), content, and time
        String sql = "SELECT u.email, m.content, m.created_at FROM messages m JOIN users u ON m.user_id = u.id WHERE m.post_id = ? ORDER BY m.created_at ASC";
        
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // We use email as the sender name for simplicity
                list.add(new Message(rs.getString("email"), rs.getString("content"), rs.getString("created_at")));
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
        return list;
    }
}