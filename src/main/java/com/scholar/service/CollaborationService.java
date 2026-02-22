package com.scholar.service;


import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class CollaborationService {

    public record Channel(int id, String title, String desc) {}
    public record Post(int id, String title, String desc, String status, int maxMembers, String ownerId) {}
    public record Requirement(int id, String question) {}
    public record Application(int id, String userId, String username, String status, List<String> answers) {}
    public record TeamResource(int id, String title, String url, String type, String desc, String fileId, String addedBy) {}
    public record Message(String sender, String content, String time) {}

    // ==========================================
    // 1. CHANNELS & POSTS
    // ==========================================

    public List<Channel> getAllChannels() {
        List<Channel> list = new ArrayList<>();
        String sql = "SELECT * FROM channels ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Channel(rs.getInt("id"), rs.getString("title"), rs.getString("description")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Post> getPostsForChannel(int channelId) {
        List<Post> list = new ArrayList<>();
        String sql = "SELECT id, title, description, status, max_members, created_by FROM posts WHERE channel_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, channelId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new Post(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("status"),
                    rs.getInt("max_members"),
                    rs.getString("created_by")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ==========================================
    // 2. MEMBER STATUS & REQUIREMENTS
    // ==========================================

    public String getMyStatus(int postId) {
        String sql = "SELECT status, role FROM team_members WHERE post_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                if ("OWNER".equals(rs.getString("role"))) return "OWNER";
                return rs.getString("status");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "NONE";
    }

    public List<Requirement> getRequirements(int postId) {
        List<Requirement> list = new ArrayList<>();
        String sql = "SELECT id, question FROM post_requirements WHERE post_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new Requirement(rs.getInt("id"), rs.getString("question")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ==========================================
    // 3. CREATE POST & APPLY (Transaction based)
    // ==========================================

    public boolean createPostWithRequirements(int channelId, String title, String desc, int maxMembers, List<String> questions) {
        String sqlPost   = "INSERT INTO posts (channel_id, title, description, max_members, created_by, status) VALUES (?, ?, ?, ?, ?, 'OPEN') RETURNING id";
        String sqlReq    = "INSERT INTO post_requirements (post_id, question) VALUES (?, ?)";
        String sqlMember = "INSERT INTO team_members (post_id, user_id, role, status) VALUES (?, ?, 'OWNER', 'APPROVED')";

        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            int postId = -1;
            try (PreparedStatement p1 = conn.prepareStatement(sqlPost)) {
                p1.setInt(1, channelId); p1.setString(2, title); p1.setString(3, desc);
                p1.setInt(4, maxMembers); p1.setObject(5, AuthService.CURRENT_USER_ID);
                ResultSet rs = p1.executeQuery();
                if (rs.next()) postId = rs.getInt(1);
            }
            if (postId != -1) {
                try (PreparedStatement p2 = conn.prepareStatement(sqlReq)) {
                    for (String q : questions) { p2.setInt(1, postId); p2.setString(2, q); p2.addBatch(); }
                    p2.executeBatch();
                }
                try (PreparedStatement p3 = conn.prepareStatement(sqlMember)) {
                    p3.setInt(1, postId); p3.setObject(2, AuthService.CURRENT_USER_ID); p3.executeUpdate();
                }
                conn.commit();
                return true;
            }
            conn.rollback();
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean applyToTeamWithAnswers(int postId, List<Integer> questionIds, List<String> answers) {
        String sqlApply  = "INSERT INTO team_members (post_id, user_id, status, role) VALUES (?, ?, 'PENDING', 'MEMBER')";
        String sqlAnswer = "INSERT INTO application_answers (post_id, user_id, question_id, answer) VALUES (?, ?, ?, ?)";
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement(sqlApply)) {
                p1.setInt(1, postId); p1.setObject(2, AuthService.CURRENT_USER_ID); p1.executeUpdate();
            }
            try (PreparedStatement p2 = conn.prepareStatement(sqlAnswer)) {
                for (int i = 0; i < questionIds.size(); i++) {
                    p2.setInt(1, postId); p2.setObject(2, AuthService.CURRENT_USER_ID);
                    p2.setInt(3, questionIds.get(i)); p2.setString(4, answers.get(i)); p2.addBatch();
                }
                p2.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ==========================================
    // 4. MEMBER MANAGEMENT
    // ==========================================

    public List<Application> getApplicationsForPost(int postId) {
        List<Application> applications = new ArrayList<>();
        String sql = """
            SELECT tm.user_id, u.username, tm.status,
                   (SELECT STRING_AGG(q.question || ': ' || a.answer, E'\\n')
                    FROM application_answers a
                    JOIN post_requirements q ON a.question_id = q.id
                    WHERE a.user_id = tm.user_id AND a.post_id = tm.post_id) as qna
            FROM team_members tm
            JOIN users u ON tm.user_id = u.id
            WHERE tm.post_id = ? AND tm.role = 'MEMBER'
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String qna = rs.getString("qna");
                List<String> qaList = (qna != null) ? List.of(qna.split("\n")) : new ArrayList<>();
                applications.add(new Application(0, rs.getString("user_id"), rs.getString("username"), rs.getString("status"), qaList));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return applications;
    }

    public boolean approveMember(int postId, String userId) {
        String sql = "UPDATE team_members SET status = 'APPROVED' WHERE post_id = ? AND user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId); pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ==========================================
    // 5. CHAT & RESOURCES
    // ==========================================

    public boolean sendMessage(int postId, String message) {
        String sql = "INSERT INTO messages (post_id, user_id, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId); pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            pstmt.setString(3, message); return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<Message> getMessages(int postId) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT u.username, m.content, m.created_at FROM messages m JOIN users u ON m.user_id = u.id WHERE m.post_id = ? ORDER BY m.created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new Message(rs.getString("username"), rs.getString("content"), rs.getString("created_at")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean addTeamResource(int postId, String title, String url) {
        String sql = "INSERT INTO team_resources (post_id, title, url, added_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId); pstmt.setString(2, title);
            pstmt.setString(3, url); pstmt.setObject(4, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean addTeamResource(int postId, String title, String url, String type, String desc, String fileId) {
        String sql = "INSERT INTO team_resources (post_id, title, url, type, description, file_id, added_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId); pstmt.setString(2, title);
            pstmt.setString(3, url); pstmt.setString(4, type);
            pstmt.setString(5, desc); pstmt.setString(6, fileId);
            pstmt.setObject(7, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<TeamResource> getTeamResources(int postId) {
        List<TeamResource> list = new ArrayList<>();
        String sql = "SELECT r.*, u.username FROM team_resources r JOIN users u ON r.added_by = u.id WHERE r.post_id = ? ORDER BY r.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new TeamResource(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("url"),
                    rs.getString("type"),
                    rs.getString("description"),
                    rs.getString("file_id"),
                    rs.getString("username")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}