package com.scholar.service;

import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CollaborationService {

    // ── Records ───────────────────────────────────────────────────────────────
    public record Channel(int id, String title, String desc) {}
    public record Post(int id, String title, String desc, String status, int maxMembers, String ownerId) {}
    public record Requirement(int id, String question) {}
    public record Application(int id, String userId, String username, String status, List<String> answers) {}
    public record TeamResource(int id, String title, String url, String type, String desc, String fileId, String addedBy) {}
    public record Message(String sender, String content, String time) {}

    // ── NEW records ────────────────────────────────────────────────────────────
    public record Plan(int id, int postId, String title, String status, String completedAt) {}
    public record PlanStep(int id, int planId, String description, String status, String completedBy, String completedAt) {}
    public record PlanHistory(int planId, String planTitle, String completedAt, List<String> completedSteps) {}
    public record TeamMember(String userId, String username, String role, String status) {}

    // ==========================================
    // 1. CHANNELS (stored in routes table)
    // ==========================================

    public boolean createChannel(String name, String information) {
        String sql = "INSERT INTO routes (channel_id, name, information) VALUES (5, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, name);
            p.setString(2, information);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<Channel> getAllChannels() {
        List<Channel> list = new ArrayList<>();
        String sql = "SELECT id, name, information FROM routes WHERE channel_id = 5 ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next())
                list.add(new Channel(rs.getInt("id"), rs.getString("name"), rs.getString("information")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Admin deletes a channel AND cascades: all posts in channel → their
     * team_members, messages, team_resources, post_requirements, application_answers,
     * plans → plan_steps.
     */
    public boolean deleteChannel(int channelId) {
        // Fetch all post IDs in this channel first
        List<Integer> postIds = getPostIdsForChannel(channelId);
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            for (int pid : postIds) deletePostCascade(conn, pid);
            try (PreparedStatement p = conn.prepareStatement(
                    "DELETE FROM routes WHERE id = ? AND channel_id = 5")) {
                p.setInt(1, channelId);
                p.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) {}
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    // ==========================================
    // 2. POSTS / TEAMS
    // ==========================================

    public List<Post> getPostsForChannel(int channelId) {
        List<Post> list = new ArrayList<>();
        String sql = "SELECT id, title, description, status, max_members, created_by FROM posts WHERE channel_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, channelId);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new Post(rs.getInt("id"), rs.getString("title"),
                        rs.getString("description"), rs.getString("status"),
                        rs.getInt("max_members"), rs.getString("created_by")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean createPostWithRequirements(int channelId, String title, String desc,
                                              int maxMembers, List<String> questions) {
        String sqlPost   = "INSERT INTO posts (channel_id, title, description, max_members, created_by, status) VALUES (?, ?, ?, ?, ?::uuid, 'OPEN') RETURNING id";
        String sqlReq    = "INSERT INTO post_requirements (post_id, question) VALUES (?, ?)";
        String sqlMember = "INSERT INTO team_members (post_id, user_id, role, status) VALUES (?, ?::uuid, 'OWNER', 'APPROVED')";
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            int postId = -1;
            try (PreparedStatement p1 = conn.prepareStatement(sqlPost)) {
                p1.setInt(1, channelId); p1.setString(2, title); p1.setString(3, desc);
                p1.setInt(4, maxMembers); p1.setString(5, AuthService.CURRENT_USER_ID.toString());
                ResultSet rs = p1.executeQuery();
                if (rs.next()) postId = rs.getInt(1);
            }
            if (postId != -1) {
                if (questions != null && !questions.isEmpty()) {
                    try (PreparedStatement p2 = conn.prepareStatement(sqlReq)) {
                        for (String q : questions) { p2.setInt(1, postId); p2.setString(2, q); p2.addBatch(); }
                        p2.executeBatch();
                    }
                }
                try (PreparedStatement p3 = conn.prepareStatement(sqlMember)) {
                    p3.setInt(1, postId); p3.setString(2, AuthService.CURRENT_USER_ID.toString());
                    p3.executeUpdate();
                }
                conn.commit();
                return true;
            }
            conn.rollback();
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ex) {}
        }
        return false;
    }

    /**
     * Only the team owner (or admin) can delete. Cascades everything.
     */
    public boolean deletePost(int postId) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            deletePostCascade(conn, postId);
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) {}
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    /** Internal cascade delete for a single post — connection must be in a transaction. */
    private void deletePostCascade(Connection conn, int postId) throws SQLException {
        // plan_steps → plans
        exec(conn, "DELETE FROM plan_steps WHERE plan_id IN (SELECT id FROM plans WHERE post_id = ?)", postId);
        exec(conn, "DELETE FROM plans WHERE post_id = ?", postId);
        exec(conn, "DELETE FROM application_answers WHERE post_id = ?", postId);
        exec(conn, "DELETE FROM post_requirements WHERE post_id = ?", postId);
        exec(conn, "DELETE FROM team_resources WHERE post_id = ?", postId);
        exec(conn, "DELETE FROM messages WHERE post_id = ?", postId);
        exec(conn, "DELETE FROM team_members WHERE post_id = ?", postId);
        exec(conn, "DELETE FROM posts WHERE id = ?", postId);
    }

    private void exec(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
        }
    }

    private List<Integer> getPostIdsForChannel(int channelId) {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement("SELECT id FROM posts WHERE channel_id = ?")) {
            p.setInt(1, channelId);
            ResultSet rs = p.executeQuery();
            while (rs.next()) ids.add(rs.getInt("id"));
        } catch (SQLException e) { e.printStackTrace(); }
        return ids;
    }

    // ==========================================
    // 3. MEMBER STATUS & REQUIREMENTS
    // ==========================================

    public String getMyStatus(int postId) {
        String sql = "SELECT status, role FROM team_members WHERE post_id = ? AND user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, AuthService.CURRENT_USER_ID.toString());
            ResultSet rs = p.executeQuery();
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
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId);
            ResultSet rs = p.executeQuery();
            while (rs.next()) list.add(new Requirement(rs.getInt("id"), rs.getString("question")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ==========================================
    // 4. APPLY
    // ==========================================

    public boolean applyToTeamWithAnswers(int postId, List<Integer> questionIds, List<String> answers) {
        // Guard: already applied?
        String check = "SELECT 1 FROM team_members WHERE post_id = ? AND user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pc = conn.prepareStatement(check)) {
            pc.setInt(1, postId); pc.setString(2, AuthService.CURRENT_USER_ID.toString());
            if (pc.executeQuery().next()) return false; // already applied/member
        } catch (SQLException e) { e.printStackTrace(); return false; }

        String sqlApply  = "INSERT INTO team_members (post_id, user_id, status, role) VALUES (?, ?::uuid, 'PENDING', 'MEMBER')";
        String sqlAnswer = "INSERT INTO application_answers (post_id, user_id, question_id, answer) VALUES (?, ?::uuid, ?, ?)";
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement(sqlApply)) {
                p1.setInt(1, postId); p1.setString(2, AuthService.CURRENT_USER_ID.toString());
                p1.executeUpdate();
            }
            if (!questionIds.isEmpty()) {
                try (PreparedStatement p2 = conn.prepareStatement(sqlAnswer)) {
                    for (int i = 0; i < questionIds.size(); i++) {
                        p2.setInt(1, postId); p2.setString(2, AuthService.CURRENT_USER_ID.toString());
                        p2.setInt(3, questionIds.get(i)); p2.setString(4, answers.get(i));
                        p2.addBatch();
                    }
                    p2.executeBatch();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) {}
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    // ==========================================
    // 5. MEMBER MANAGEMENT
    // ==========================================

    public List<TeamMember> getTeamMembers(int postId) {
        List<TeamMember> list = new ArrayList<>();
        String sql = """
            SELECT tm.user_id, u.username, tm.role, tm.status
            FROM team_members tm
            JOIN users u ON tm.user_id = u.id
            WHERE tm.post_id = ?
            ORDER BY tm.role ASC, u.username ASC
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new TeamMember(rs.getString("user_id"), rs.getString("username"),
                        rs.getString("role"), rs.getString("status")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

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
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                String qna = rs.getString("qna");
                List<String> qaList = (qna != null) ? List.of(qna.split("\n")) : new ArrayList<>();
                applications.add(new Application(0, rs.getString("user_id"),
                        rs.getString("username"), rs.getString("status"), qaList));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return applications;
    }

    /**
     * Approve only if still PENDING — prevents duplicate rows.
     */
    public boolean approveMember(int postId, String userId) {
        String sql = "UPDATE team_members SET status = 'APPROVED' WHERE post_id = ? AND user_id = ?::uuid AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, userId);
            return p.executeUpdate() > 0; // 0 means already approved → no-op, no duplicate
        } catch (SQLException e) { return false; }
    }

    /**
     * Reject (remove) a PENDING application.
     */
    public boolean rejectMember(int postId, String userId) {
        String sqlDel = "DELETE FROM team_members WHERE post_id = ? AND user_id = ?::uuid AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sqlDel)) {
            p.setInt(1, postId); p.setString(2, userId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    /**
     * Admin or owner kicks any member (cannot kick owner).
     */
    public boolean kickMember(int postId, String userId) {
        String sql = "DELETE FROM team_members WHERE post_id = ? AND user_id = ?::uuid AND role != 'OWNER'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, userId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    /**
     * Any member leaves a team they belong to (owner cannot leave).
     */
    public boolean leaveTeam(int postId) {
        String sql = "DELETE FROM team_members WHERE post_id = ? AND user_id = ?::uuid AND role != 'OWNER'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, AuthService.CURRENT_USER_ID.toString());
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    /**
     * Returns true if a user is already an APPROVED member of ANY team in a given channel/route.
     * Used to enforce: "only users with no team in this channel can join another".
     */
    public boolean isUserInAnyTeamUnderChannel(int channelId) {
        String sql = """
            SELECT 1 FROM team_members tm
            JOIN posts p ON tm.post_id = p.id
            WHERE p.channel_id = ? AND tm.user_id = ?::uuid AND tm.status = 'APPROVED'
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, channelId); p.setString(2, AuthService.CURRENT_USER_ID.toString());
            return p.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ==========================================
    // 6. CHAT
    // ==========================================

    public boolean sendMessage(int postId, String message) {
        String sql = "INSERT INTO messages (post_id, user_id, content) VALUES (?, ?::uuid, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, AuthService.CURRENT_USER_ID.toString());
            p.setString(3, message); return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<Message> getMessages(int postId) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT u.username, m.content, m.created_at FROM messages m JOIN users u ON m.user_id = u.id WHERE m.post_id = ? ORDER BY m.created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new Message(rs.getString("username"), rs.getString("content"), rs.getString("created_at")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ==========================================
    // 7. RESOURCES
    // ==========================================

    public boolean addTeamResource(int postId, String title, String url) {
        String sql = "INSERT INTO team_resources (post_id, title, url, added_by) VALUES (?, ?, ?, ?::uuid)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, title); p.setString(3, url);
            p.setString(4, AuthService.CURRENT_USER_ID.toString());
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean addTeamResource(int postId, String title, String url, String type, String desc, String fileId) {
        String sql = "INSERT INTO team_resources (post_id, title, url, type, description, file_id, added_by) VALUES (?, ?, ?, ?, ?, ?, ?::uuid)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, title); p.setString(3, url); p.setString(4, type);
            p.setString(5, desc); p.setString(6, fileId); p.setString(7, AuthService.CURRENT_USER_ID.toString());
            return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<TeamResource> getTeamResources(int postId) {
        List<TeamResource> list = new ArrayList<>();
        String sql = "SELECT r.*, u.username FROM team_resources r JOIN users u ON r.added_by = u.id WHERE r.post_id = ? ORDER BY r.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new TeamResource(rs.getInt("id"), rs.getString("title"), rs.getString("url"),
                        rs.getString("type"), rs.getString("description"),
                        rs.getString("file_id"), rs.getString("username")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ==========================================
    // 8. PLANS
    // ==========================================

    public boolean createPlan(int postId, String title) {
        String sql = "INSERT INTO plans (post_id, title, status, created_by) VALUES (?, ?, 'ACTIVE', ?::uuid)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId); p.setString(2, title);
            p.setString(3, AuthService.CURRENT_USER_ID.toString());
            return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<Plan> getPlans(int postId) {
        List<Plan> list = new ArrayList<>();
        String sql = "SELECT id, post_id, title, status, completed_at FROM plans WHERE post_id = ? ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, postId);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new Plan(rs.getInt("id"), rs.getInt("post_id"),
                        rs.getString("title"), rs.getString("status"),
                        rs.getString("completed_at")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean addStep(int planId, String description) {
        String sql = "INSERT INTO plan_steps (plan_id, description, status) VALUES (?, ?, 'PENDING')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, planId); p.setString(2, description);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<PlanStep> getSteps(int planId) {
        List<PlanStep> list = new ArrayList<>();
        String sql = """
            SELECT ps.id, ps.plan_id, ps.description, ps.status,
                   u.username AS completed_by, ps.completed_at
            FROM plan_steps ps
            LEFT JOIN users u ON ps.completed_by = u.id
            WHERE ps.plan_id = ?
            ORDER BY ps.id ASC
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, planId);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new PlanStep(rs.getInt("id"), rs.getInt("plan_id"),
                        rs.getString("description"), rs.getString("status"),
                        rs.getString("completed_by"), rs.getString("completed_at")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Mark step done. Then check if ALL steps in plan are done → mark plan COMPLETED.
     */
    public boolean completeStep(int stepId, int planId) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            // Mark step
            try (PreparedStatement p = conn.prepareStatement(
                    "UPDATE plan_steps SET status = 'DONE', completed_by = ?::uuid, completed_at = NOW() WHERE id = ?")) {
                p.setString(1, AuthService.CURRENT_USER_ID.toString());
                p.setInt(2, stepId);
                p.executeUpdate();
            }
            // Check if all steps done
            boolean allDone;
            try (PreparedStatement p = conn.prepareStatement(
                    "SELECT COUNT(*) FROM plan_steps WHERE plan_id = ? AND status != 'DONE'")) {
                p.setInt(1, planId);
                ResultSet rs = p.executeQuery();
                rs.next();
                allDone = rs.getInt(1) == 0;
            }
            if (allDone) {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE plans SET status = 'COMPLETED', completed_at = NOW() WHERE id = ?")) {
                    p.setInt(1, planId);
                    p.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) {}
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    public boolean deleteStep(int stepId) {
        String sql = "DELETE FROM plan_steps WHERE id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, stepId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    /**
     * Plan history: all COMPLETED plans with their finished steps.
     */
    public List<PlanHistory> getPlanHistory(int postId) {
        List<PlanHistory> list = new ArrayList<>();
        String sqlPlans = "SELECT id, title, completed_at FROM plans WHERE post_id = ? AND status = 'COMPLETED' ORDER BY completed_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pp = conn.prepareStatement(sqlPlans)) {
            pp.setInt(1, postId);
            ResultSet rp = pp.executeQuery();
            while (rp.next()) {
                int planId = rp.getInt("id");
                String planTitle = rp.getString("title");
                String completedAt = rp.getString("completed_at");
                List<String> steps = new ArrayList<>();
                String sqlSteps = """
                    SELECT ps.description, u.username, ps.completed_at
                    FROM plan_steps ps
                    LEFT JOIN users u ON ps.completed_by = u.id
                    WHERE ps.plan_id = ?
                    ORDER BY ps.completed_at ASC
                """;
                try (PreparedStatement ps = conn.prepareStatement(sqlSteps)) {
                    ps.setInt(1, planId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next())
                        steps.add("✓ " + rs.getString("description")
                                + " — by " + rs.getString("username")
                                + " at " + rs.getString("completed_at"));
                }
                list.add(new PlanHistory(planId, planTitle, completedAt, steps));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}