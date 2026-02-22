package com.scholar.service;

import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
@Service
public class ChannelService {

    @Autowired
    private DataSource dataSource; // 🟢 Spring Boot-এর কনফিগার করা কানেকশন পুল

    // -------------------------------------------------------------------
    // Path A: ADMIN REGISTRATION
    // -------------------------------------------------------------------
    public String registerAsAdmin(String name, String email, String password, String channelName, String channelCode) {
        // DriverManager-এর বদলে dataSource থেকে কানেকশন নেওয়া হচ্ছে
        try (Connection conn = dataSource.getConnection()) {
            try {
                conn.setAutoCommit(false); 

                // 1. Create User in public.users
                UUID userId = UUID.randomUUID();
                String userSql = "INSERT INTO public.users (id, email, password) VALUES (?, ?, ?)";
                try (PreparedStatement pst = conn.prepareStatement(userSql)) {
                    pst.setObject(1, userId);
                    pst.setString(2, email);
                    pst.setString(3, password);
                    pst.executeUpdate();
                }

                // 2. Create Channel
                String chanSql = "INSERT INTO channels (name, unique_code, created_by) VALUES (?, ?, ?) RETURNING id";
                int channelId = -1;
                try (PreparedStatement pst = conn.prepareStatement(chanSql)) {
                    pst.setString(1, channelName);
                    pst.setString(2, channelCode);
                    pst.setObject(3, userId);
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) channelId = rs.getInt(1);
                }

                // 3. Make User the ADMIN
                String memSql = "INSERT INTO channel_members (user_id, channel_id, role, status) VALUES (?, ?, 'admin', 'approved')";
                try (PreparedStatement pst = conn.prepareStatement(memSql)) {
                    pst.setObject(1, userId);
                    pst.setInt(2, channelId);
                    pst.executeUpdate();
                }

                conn.commit();
                return "SUCCESS";
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) {}
                return e.getMessage();
            }
        } catch (SQLException e) {
            return "Connection Error: " + e.getMessage();
        }
    }


    // Path B: STUDENT REGISTRATION
    public String registerAsStudent(String name, String email, String password, String channelCode) {
        try (Connection conn = dataSource.getConnection()) {
            int channelId = -1;
            try (PreparedStatement pst = conn.prepareStatement("SELECT id FROM channels WHERE unique_code = ?")) {
                pst.setString(1, channelCode);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) channelId = rs.getInt("id");
                else return "INVALID_CODE";
            }

            try {
                conn.setAutoCommit(false);
                UUID userId = UUID.randomUUID();
                String userSql = "INSERT INTO public.users (id, email, password) VALUES (?, ?, ?)";
                try (PreparedStatement pst = conn.prepareStatement(userSql)) {
                    pst.setObject(1, userId);
                    pst.setString(2, email);
                    pst.setString(3, password);
                    pst.executeUpdate();
                }

                String joinSql = "INSERT INTO channel_members (user_id, channel_id, role, status) VALUES (?, ?, 'student', 'pending')";
                try (PreparedStatement pst = conn.prepareStatement(joinSql)) {
                    pst.setObject(1, userId);
                    pst.setInt(2, channelId);
                    pst.executeUpdate();
                }

                conn.commit();
                return "SUCCESS";
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) {}
                return e.getMessage();
            }
        } catch (SQLException e) {
            return "Connection Error: " + e.getMessage();
        }
    }



    public boolean createChannel(String name, String code) {
        try (Connection conn = dataSource.getConnection()) { // 🟢 Updated
            try {
                conn.setAutoCommit(false);
                String sqlChannel = "INSERT INTO channels (name, unique_code, created_by) VALUES (?, ?, ?) RETURNING id";
                int channelId = -1;
                try (PreparedStatement pst = conn.prepareStatement(sqlChannel)) {
                    pst.setString(1, name);
                    pst.setString(2, code);
                    pst.setObject(3, AuthService.CURRENT_USER_ID);
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) channelId = rs.getInt(1);
                }

                if (channelId != -1) {
                    String sqlMember = "INSERT INTO channel_members (user_id, channel_id, role, status) VALUES (?, ?, 'admin', 'approved')";
                    try (PreparedStatement pst = conn.prepareStatement(sqlMember)) {
                        pst.setObject(1, AuthService.CURRENT_USER_ID);
                        pst.setInt(2, channelId);
                        pst.executeUpdate();
                    }
                    conn.commit();
                    return true;
                }
                return false;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) {}
                return false;
            }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

 public String joinChannel(String code) {
        try (Connection conn = dataSource.getConnection()) { // 🟢 Updated
            int channelId = -1;
            try (PreparedStatement pst = conn.prepareStatement("SELECT id FROM channels WHERE unique_code = ?")) {
                pst.setString(1, code);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) channelId = rs.getInt("id");
                else return "INVALID_CODE";
            }

            String sql = "INSERT INTO channel_members (user_id, channel_id, role, status) VALUES (?, ?, 'student', 'pending')";
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setObject(1, AuthService.CURRENT_USER_ID);
                pst.setInt(2, channelId);
                pst.executeUpdate();
                return "SUCCESS";
            }
        } catch (SQLException e) { return "ALREADY_JOINED"; }
    }





    //// ChannelService.java এর ভেতরে যোগ করুন

// ১. পেন্ডিং স্টুডেন্টদের লিস্ট নিয়ে আসা
public List<String[]> getPendingMembers(int channelId) {
        List<String[]> students = new ArrayList<>();
        String sql = """
            SELECT u.id, u.email 
            FROM channel_members cm
            JOIN public.users u ON cm.user_id = u.id
            WHERE cm.channel_id = ? AND cm.status = 'pending'
        """;

        try (Connection conn = dataSource.getConnection(); // 🟢 Updated
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, channelId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                students.add(new String[]{rs.getString("id"), rs.getString("email")});
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return students;
    }

    public boolean updateMemberStatus(String userId, int channelId, String newStatus) {
        String sql = (newStatus.equals("rejected")) 
            ? "DELETE FROM channel_members WHERE user_id = ?::uuid AND channel_id = ?" 
            : "UPDATE channel_members SET status = ? WHERE user_id = ?::uuid AND channel_id = ?";

        try (Connection conn = dataSource.getConnection(); // 🟢 Updated
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (newStatus.equals("rejected")) {
                pstmt.setString(1, userId);
                pstmt.setInt(2, channelId);
            } else {
                pstmt.setString(1, newStatus);
                pstmt.setString(2, userId);
                pstmt.setInt(3, channelId);
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }


}