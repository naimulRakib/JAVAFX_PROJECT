package com.scholar.service;

import com.scholar.model.AppNotification;
import com.scholar.model.ChatMessage;
import com.scholar.model.DailyThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class SocialZoneService {

    @Autowired private DataService dataService;

    // ─────────────────────────────────────────────
    //  GROUP CHAT
    // ─────────────────────────────────────────────

    public boolean sendMessage(String content) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        String sql = "INSERT INTO social_messages (sender_id, sender_name, content) VALUES (?::uuid, ?, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            p.setString(2, AuthService.CURRENT_USER_NAME != null ? AuthService.CURRENT_USER_NAME : "Student");
            p.setString(3, content);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<ChatMessage> getRecentMessages() {
        List<ChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM social_messages ORDER BY created_at ASC LIMIT 50";
        try (Connection conn = dataService.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                boolean isMe = AuthService.CURRENT_USER_ID != null &&
                        rs.getString("sender_id").equals(AuthService.CURRENT_USER_ID.toString());
                list.add(new ChatMessage(
                        rs.getString("id"), rs.getString("sender_id"), rs.getString("sender_name"),
                        rs.getString("content"), rs.getString("created_at"), isMe, rs.getBoolean("is_admin_msg")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ─────────────────────────────────────────────
    //  PRIVATE MESSAGES
    // ─────────────────────────────────────────────

    public boolean sendPrivateMessage(String receiverId, String content) {
        String sql = "INSERT INTO private_messages (sender_id, receiver_id, content) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            p.setString(2, receiverId);
            p.setString(3, content);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<ChatMessage> getPrivateMessages(String contactId) {
        List<ChatMessage> list = new ArrayList<>();
        String sql = "SELECT m.*, u.username as sender_name FROM private_messages m " +
                     "JOIN users u ON m.sender_id = u.id " +
                     "WHERE (m.sender_id = ?::uuid AND m.receiver_id = ?::uuid) " +
                     "   OR (m.sender_id = ?::uuid AND m.receiver_id = ?::uuid) " +
                     "ORDER BY m.created_at ASC";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID); p.setString(2, contactId);
            p.setString(3, contactId); p.setObject(4, AuthService.CURRENT_USER_ID);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                boolean isMe = rs.getString("sender_id").equals(AuthService.CURRENT_USER_ID.toString());
                list.add(new ChatMessage(rs.getString("id"), rs.getString("sender_id"),
                        rs.getString("sender_name"), rs.getString("content"),
                        rs.getString("created_at"), isMe, false));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ─────────────────────────────────────────────
    //  CHAT REQUESTS & CONTACTS
    // ─────────────────────────────────────────────

    public List<com.scholar.model.ChatRequest> getPendingRequests() {
        List<com.scholar.model.ChatRequest> list = new ArrayList<>();
        String sql = "SELECT r.id, r.sender_id, u.username FROM chat_requests r " +
                     "JOIN users u ON r.sender_id = u.id " +
                     "WHERE r.receiver_id = ?::uuid AND r.status = 'PENDING'";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new com.scholar.model.ChatRequest(
                        rs.getString("id"), rs.getString("sender_id"), rs.getString("username"), "PENDING"));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean acceptRequest(String requestId) {
        String sql = "UPDATE chat_requests SET status = 'ACCEPTED' WHERE id = ?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, requestId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<com.scholar.model.PrivateContact> getAcceptedContacts() {
        List<com.scholar.model.PrivateContact> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username FROM chat_requests r " +
                     "JOIN users u ON (r.sender_id = u.id OR r.receiver_id = u.id) " +
                     "WHERE (r.sender_id = ?::uuid OR r.receiver_id = ?::uuid) " +
                     "  AND u.id != ?::uuid AND r.status = 'ACCEPTED'";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            p.setObject(2, AuthService.CURRENT_USER_ID);
            p.setObject(3, AuthService.CURRENT_USER_ID);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new com.scholar.model.PrivateContact(rs.getString("id"), rs.getString("username")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<com.scholar.model.PrivateContact> getChannelMembers(int channelId) {
        List<com.scholar.model.PrivateContact> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username FROM channel_members cm " +
                     "JOIN users u ON cm.user_id = u.id " +
                     "WHERE cm.channel_id = ? AND cm.status = 'approved' AND u.id != ?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, channelId);
            p.setObject(2, AuthService.CURRENT_USER_ID);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new com.scholar.model.PrivateContact(rs.getString("id"), rs.getString("username")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean sendChatRequestById(String receiverId) {
        String sql = "INSERT INTO chat_requests (sender_id, receiver_id) VALUES (?::uuid, ?::uuid)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            p.setString(2, receiverId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Request Error/Already Sent: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────
    //  POLLING SYSTEM
    // ─────────────────────────────────────────────

    public boolean createPoll(String question, List<String> options) {
        if (question == null || question.trim().isEmpty() || options == null || options.size() < 2) {
            System.err.println("❌ createPoll: need a question and at least 2 options");
            return false;
        }
        String insertPoll = "INSERT INTO channel_polls (channel_id, creator_id, question) VALUES (?, ?::uuid, ?) RETURNING id";
        String insertOpt  = "INSERT INTO poll_options (poll_id, option_text) VALUES (?::uuid, ?)";

        try (Connection conn = dataService.connect()) {
            conn.setAutoCommit(false);
            try {
                // 1. Insert poll, get ID
                String pollId;
                try (PreparedStatement ps = conn.prepareStatement(insertPoll)) {
                    ps.setInt(1, AuthService.CURRENT_CHANNEL_ID);
                    ps.setObject(2, AuthService.CURRENT_USER_ID);
                    ps.setString(3, question.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        pollId = rs.getString("id");
                    }
                }
                // 2. Batch insert options
                try (PreparedStatement ps = conn.prepareStatement(insertOpt)) {
                    for (String opt : options) {
                        if (opt == null || opt.trim().isEmpty()) continue;
                        ps.setString(1, pollId);
                        ps.setString(2, opt.trim());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                System.out.println("✅ Poll created: " + pollId);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ createPoll rollback: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("❌ createPoll connection error: " + e.getMessage());
            return false;
        }
    }

    public List<com.scholar.model.Poll> getChannelPolls() {
        List<com.scholar.model.Poll> polls = new ArrayList<>();

        String pollSql =
            "SELECT p.id, p.question, u.username " +
            "FROM channel_polls p " +
            "JOIN users u ON p.creator_id = u.id " +
            "WHERE p.channel_id = ? " +
            "ORDER BY p.created_at DESC";

        String optSql =
            "SELECT o.id, o.option_text, " +
            "  (SELECT COUNT(*) FROM poll_votes v WHERE v.option_id = o.id) AS votes, " +
            "  (SELECT COUNT(*) FROM poll_votes v WHERE v.option_id = o.id AND v.user_id = ?::uuid) AS my_vote " +
            "FROM poll_options o " +
            "WHERE o.poll_id = ?::uuid";

        try (Connection conn = dataService.connect();
             PreparedStatement psPoll = conn.prepareStatement(pollSql)) {

            psPoll.setInt(1, AuthService.CURRENT_CHANNEL_ID);
            ResultSet rsPoll = psPoll.executeQuery();

            while (rsPoll.next()) {
                String pollId      = rsPoll.getString("id");
                String question    = rsPoll.getString("question");
                String creatorName = rsPoll.getString("username");
                List<com.scholar.model.PollOption> optList = new ArrayList<>();
                int totalVotes = 0;

                try (PreparedStatement psOpt = conn.prepareStatement(optSql)) {
                    psOpt.setString(1, AuthService.CURRENT_USER_ID.toString());
                    psOpt.setString(2, pollId);
                    ResultSet rsOpt = psOpt.executeQuery();
                    while (rsOpt.next()) {
                        int votes  = rsOpt.getInt("votes");
                        totalVotes += votes;
                        optList.add(new com.scholar.model.PollOption(
                                rsOpt.getString("id"),
                                rsOpt.getString("option_text"),
                                votes,
                                rsOpt.getInt("my_vote") > 0));
                    }
                }
                polls.add(new com.scholar.model.Poll(pollId, question, creatorName, optList, totalVotes));
            }
        } catch (SQLException e) {
            System.err.println("❌ getChannelPolls: " + e.getMessage());
            e.printStackTrace();
        }
        return polls;
    }

    public boolean castVote(String pollId, String optionId) {
        String sql = "INSERT INTO poll_votes (poll_id, option_id, user_id) VALUES (?::uuid, ?::uuid, ?::uuid) " +
                     "ON CONFLICT (poll_id, user_id) DO UPDATE SET option_id = EXCLUDED.option_id";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, pollId);
            p.setString(2, optionId);
            p.setString(3, AuthService.CURRENT_USER_ID.toString());
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ castVote: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePoll(String pollId) {
        try (Connection conn = dataService.connect()) {
            conn.setAutoCommit(false);
            try {
                for (String sql : new String[]{
                        "DELETE FROM poll_votes WHERE poll_id = ?::uuid",
                        "DELETE FROM poll_options WHERE poll_id = ?::uuid",
                        "DELETE FROM channel_polls WHERE id = ?::uuid"}) {
                    try (PreparedStatement p = conn.prepareStatement(sql)) {
                        p.setString(1, pollId);
                        p.executeUpdate();
                    }
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ deletePoll: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) { return false; }
    }

    public boolean addPollOption(String pollId, String optionText) {
        if (optionText == null || optionText.trim().isEmpty()) return false;
        String sql = "INSERT INTO poll_options (poll_id, option_text) VALUES (?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, pollId);
            p.setString(2, optionText.trim());
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ addPollOption: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────
    //  DAILY THREADS
    // ─────────────────────────────────────────────

    public String createDailyThread(String content, String mediaUrl, String photoUrl) {
        return createDailyThread(content, mediaUrl, photoUrl, "PUBLIC");
    }

    public String createDailyThread(String content, String mediaUrl, String photoUrl, String category) {
        String sql = "INSERT INTO daily_threads (user_id, content_text, media_url, photo_url, category) " +
                     "VALUES (?::uuid, ?, ?, ?, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            p.setString(2, content  != null ? content  : "");
            p.setString(3, mediaUrl != null ? mediaUrl : "");
            p.setString(4, photoUrl != null ? photoUrl : "");
            p.setString(5, category != null ? category.toUpperCase() : "PUBLIC");
            p.executeUpdate();
            return "SUCCESS";
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) return "ALREADY_POSTED";
            e.printStackTrace();
            return "ERROR";
        }
    }

    public List<DailyThread> getThreadsByCategory(String category) {
        List<DailyThread> threads = new ArrayList<>();
        String upperCat = category != null ? category.toUpperCase() : "HOME";
        String sql;

        switch (upperCat) {
            case "HOME" ->
                sql = "SELECT t.*, u.username as author_name, COALESCE(t.like_count,0) as like_count, " +
                      "(SELECT COUNT(*) FROM thread_saves s WHERE s.thread_id=t.id AND s.user_id=?::uuid) as saved_by_me " +
                      "FROM daily_threads t JOIN users u ON t.user_id=u.id ORDER BY t.created_at DESC LIMIT 50";
            case "MY_THREADS" ->
                sql = "SELECT t.*, u.username as author_name, COALESCE(t.like_count,0) as like_count, " +
                      "(SELECT COUNT(*) FROM thread_saves s WHERE s.thread_id=t.id AND s.user_id=?::uuid) as saved_by_me " +
                      "FROM daily_threads t JOIN users u ON t.user_id=u.id " +
                      "WHERE t.user_id=?::uuid ORDER BY t.created_at DESC";
            case "SAVED" ->
                sql = "SELECT t.*, u.username as author_name, COALESCE(t.like_count,0) as like_count, 1 as saved_by_me " +
                      "FROM daily_threads t JOIN users u ON t.user_id=u.id " +
                      "JOIN thread_saves s ON s.thread_id=t.id WHERE s.user_id=?::uuid ORDER BY s.saved_at DESC";
            default ->
                sql = "SELECT t.*, u.username as author_name, COALESCE(t.like_count,0) as like_count, " +
                      "(SELECT COUNT(*) FROM thread_saves s WHERE s.thread_id=t.id AND s.user_id=?::uuid) as saved_by_me " +
                      "FROM daily_threads t JOIN users u ON t.user_id=u.id " +
                      "WHERE UPPER(t.category)=? ORDER BY t.created_at DESC LIMIT 50";
        }

        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            if (upperCat.equals("MY_THREADS")) {
                p.setObject(2, AuthService.CURRENT_USER_ID);
            } else if (!upperCat.equals("HOME") && !upperCat.equals("SAVED")) {
                p.setString(2, upperCat);
            }
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                threads.add(new DailyThread(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("author_name"),
                        rs.getString("content_text"),
                        rs.getString("media_url"),
                        rs.getString("photo_url"),
                        safeGetString(rs, "category"),
                        safeGetInt(rs, "like_count"),
                        safeGetInt(rs, "saved_by_me") > 0,
                        rs.getString("created_at")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return threads;
    }

    public boolean likeThread(String threadId) {
        String sql = "INSERT INTO thread_likes (thread_id, user_id) VALUES (?::uuid, ?::uuid) ON CONFLICT DO NOTHING";
        String upd  = "UPDATE daily_threads SET like_count=(SELECT COUNT(*) FROM thread_likes WHERE thread_id=?::uuid) WHERE id=?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p1 = conn.prepareStatement(sql);
             PreparedStatement p2 = conn.prepareStatement(upd)) {
            p1.setString(1, threadId); p1.setObject(2, AuthService.CURRENT_USER_ID); p1.executeUpdate();
            p2.setString(1, threadId); p2.setString(2, threadId); p2.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean saveThread(String threadId) {
        String sql = "INSERT INTO thread_saves (thread_id, user_id) VALUES (?::uuid, ?::uuid) ON CONFLICT DO NOTHING";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, threadId); p.setObject(2, AuthService.CURRENT_USER_ID);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ─────────────────────────────────────────────
    //  NOTIFICATIONS
    // ─────────────────────────────────────────────

    public List<AppNotification> getNotifications() {
        List<AppNotification> list = new ArrayList<>();
        String sql = "SELECT * FROM app_notifications WHERE user_id=?::uuid ORDER BY created_at DESC LIMIT 50";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                list.add(new AppNotification(
                        rs.getString("id"), rs.getString("type"),
                        rs.getString("title"), rs.getString("body"),
                        rs.getBoolean("is_read"), formatTimeAgo(rs.getString("created_at"))));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public int getUnreadNotificationCount() {
        String sql = "SELECT COUNT(*) FROM app_notifications WHERE user_id=?::uuid AND is_read=false";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public boolean markNotificationRead(String notifId) {
        String sql = "UPDATE app_notifications SET is_read=true WHERE id=?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, notifId); return p.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public void markAllNotificationsRead() {
        String sql = "UPDATE app_notifications SET is_read=true WHERE user_id=?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, AuthService.CURRENT_USER_ID); p.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private String safeGetString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException e) { return null; }
    }

    private int safeGetInt(ResultSet rs, String col) {
        try { return rs.getInt(col); } catch (SQLException e) { return 0; }
    }

    private String formatTimeAgo(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            String iso = createdAt.replace(" ", "T");
            if (!iso.contains("+") && !iso.endsWith("Z")) iso += "Z";
            java.time.OffsetDateTime dt = java.time.OffsetDateTime.parse(iso);
            long mins = java.time.Duration.between(dt, java.time.OffsetDateTime.now()).toMinutes();
            if (mins < 1)    return "just now";
            if (mins < 60)   return mins + "m ago";
            if (mins < 1440) return (mins / 60) + "h ago";
            return (mins / 1440) + "d ago";
        } catch (Exception e) {
            return createdAt.substring(0, Math.min(10, createdAt.length()));
        }
    }
}