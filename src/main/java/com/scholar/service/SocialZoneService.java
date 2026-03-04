package com.scholar.service;

import com.scholar.model.AppNotification;
import com.scholar.model.ChatMessage;
import com.scholar.model.DailyThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SocialZoneService {

    @Autowired private DataService         dataService;
    @Autowired private ChatSettingsService chatSettingsService;
    @Autowired private ProfileService      profileService; // ← for profile popups

    // ─────────────────────────────────────────────────────────
    //  GUARD
    // ─────────────────────────────────────────────────────────

    private UUID requireUid() {
        UUID uid = AuthService.CURRENT_USER_ID;
        if (uid == null) throw new IllegalStateException("Not logged in");
        return uid;
    }

    // ─────────────────────────────────────────────────────────
    //  GROUP CHAT
    // ─────────────────────────────────────────────────────────

    /** Returns "SENT", "DISABLED", or "ERROR". Always call this instead of sendMessage(). */
    public String sendMessageGuarded(String content) {
        if (!isSendingAllowed()) return "DISABLED";
        return sendMessage(content) ? "SENT" : "ERROR";
    }

    public boolean isSendingAllowed() {
        if ("admin".equals(AuthService.CURRENT_USER_ROLE)) return true;
        return chatSettingsService.isPublicChatEnabled();
    }

    public boolean sendMessage(String content) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        String sql = """
                INSERT INTO social_messages (sender_id, sender_name, content)
                VALUES (?::uuid, ?, ?)
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            p.setString(2, AuthService.CURRENT_USER_NAME != null ? AuthService.CURRENT_USER_NAME : "Student");
            p.setString(3, content);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ sendMessage: " + e.getMessage());
            return false;
        }
    }

    public List<ChatMessage> getRecentMessages() {
        List<ChatMessage> list = new ArrayList<>();
        // JOIN users to always get the freshest username & avatar URL
        String sql = """
                SELECT m.id, m.sender_id, m.content, m.created_at, m.is_admin_msg,
                       COALESCE(u.username, m.sender_name) AS sender_name,
                       p.profile_picture_url
                FROM social_messages m
                LEFT JOIN users u    ON m.sender_id = u.id
                LEFT JOIN profiles p ON m.sender_id = p.user_id
                ORDER BY m.created_at ASC
                LIMIT 100
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            UUID me = AuthService.CURRENT_USER_ID;
            while (rs.next()) {
                String senderId = rs.getString("sender_id");
                boolean isMe    = me != null && me.toString().equals(senderId);
                list.add(new ChatMessage(
                        rs.getString("id"),
                        senderId,
                        rs.getString("sender_name"),
                        rs.getString("content"),
                        rs.getString("created_at"),
                        isMe,
                        rs.getBoolean("is_admin_msg"),
                        rs.getString("profile_picture_url")  // ← avatar for bubbles
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ getRecentMessages: " + e.getMessage());
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────
    //  PRIVATE MESSAGES
    // ─────────────────────────────────────────────────────────

    public boolean sendPrivateMessage(String receiverId, String content) {
        String sql = "INSERT INTO private_messages (sender_id, receiver_id, content) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            p.setString(2, receiverId);
            p.setString(3, content);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ sendPrivateMessage: " + e.getMessage());
            return false;
        }
    }

    public List<ChatMessage> getPrivateMessages(String contactId) {
        List<ChatMessage> list = new ArrayList<>();
        String sql = """
                SELECT m.id, m.sender_id, m.content, m.created_at,
                       COALESCE(u.username, m.sender_id::text) AS sender_name,
                       p.profile_picture_url
                FROM private_messages m
                LEFT JOIN users u    ON m.sender_id = u.id
                LEFT JOIN profiles p ON m.sender_id = p.user_id
                WHERE (m.sender_id = ?::uuid AND m.receiver_id = ?::uuid)
                   OR (m.sender_id = ?::uuid AND m.receiver_id = ?::uuid)
                ORDER BY m.created_at ASC
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            UUID me = requireUid();
            p.setObject(1, me);  p.setString(2, contactId);
            p.setString(3, contactId); p.setObject(4, me);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    boolean isMe = rs.getString("sender_id").equals(me.toString());
                    list.add(new ChatMessage(
                            rs.getString("id"), rs.getString("sender_id"),
                            rs.getString("sender_name"), rs.getString("content"),
                            rs.getString("created_at"), isMe, false,
                            rs.getString("profile_picture_url")));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ getPrivateMessages: " + e.getMessage());
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────
    //  CHAT REQUESTS & CONTACTS
    // ─────────────────────────────────────────────────────────

    public List<com.scholar.model.ChatRequest> getPendingRequests() {
        List<com.scholar.model.ChatRequest> list = new ArrayList<>();
        String sql = """
                SELECT r.id, r.sender_id, COALESCE(u.username, 'Unknown') AS username,
                       p.profile_picture_url
                FROM chat_requests r
                JOIN users u    ON r.sender_id = u.id
                LEFT JOIN profiles p ON r.sender_id = p.user_id
                WHERE r.receiver_id = ?::uuid AND r.status = 'PENDING'
                ORDER BY r.created_at DESC
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next())
                    list.add(new com.scholar.model.ChatRequest(
                            rs.getString("id"), rs.getString("sender_id"),
                            rs.getString("username"), "PENDING",
                            rs.getString("profile_picture_url")));
            }
        } catch (SQLException e) {
            System.err.println("❌ getPendingRequests: " + e.getMessage());
        }
        return list;
    }

    /** Batch-fetches connection statuses for a list of user IDs (avoids N+1). */
    public Map<String, String> getBatchConnectionStatuses(List<String> targetUserIds) {
        Map<String, String> result = new HashMap<>();
        if (targetUserIds == null || targetUserIds.isEmpty()) return result;

        // Pre-fill with NONE
        for (String id : targetUserIds) result.put(id, "NONE");

        UUID me = AuthService.CURRENT_USER_ID;
        if (me == null) return result;

        // Build a single query using ANY(array)
        String sql = """
                SELECT sender_id::text, receiver_id::text, status
                FROM chat_requests
                WHERE (sender_id = ?::uuid   AND receiver_id = ANY(?::uuid[]))
                   OR (receiver_id = ?::uuid AND sender_id   = ANY(?::uuid[]))
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            String[] ids = targetUserIds.toArray(new String[0]);
            p.setObject(1, me);
            p.setArray(2, conn.createArrayOf("uuid", ids));
            p.setObject(3, me);
            p.setArray(4, conn.createArrayOf("uuid", ids));
            try (ResultSet rs = p.executeQuery()) {
                String myId = me.toString();
                while (rs.next()) {
                    String sender   = rs.getString("sender_id");
                    String receiver = rs.getString("receiver_id");
                    String status   = rs.getString("status");
                    String otherId  = myId.equals(sender) ? receiver : sender;
                    result.put(otherId, status);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ getBatchConnectionStatuses: " + e.getMessage());
        }
        return result;
    }

    /** Single-user connection status (kept for targeted use). */
    public String getConnectionStatus(String targetUserId) {
        String sql = """
                SELECT status FROM chat_requests
                WHERE (sender_id = ?::uuid AND receiver_id = ?::uuid)
                   OR (sender_id = ?::uuid AND receiver_id = ?::uuid)
                LIMIT 1
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            UUID me = requireUid();
            p.setObject(1, me); p.setString(2, targetUserId);
            p.setString(3, targetUserId); p.setObject(4, me);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString("status");
            }
        } catch (SQLException e) {
            System.err.println("❌ getConnectionStatus: " + e.getMessage());
        }
        return "NONE";
    }

    public boolean acceptRequest(String requestId) {
        String sql = "UPDATE chat_requests SET status = 'ACCEPTED' WHERE id = ?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, requestId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ acceptRequest: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteChatRequest(String requestId) {
        String sql = "DELETE FROM chat_requests WHERE id = ?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, requestId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ deleteChatRequest: " + e.getMessage());
            return false;
        }
    }

    public boolean sendChatRequestById(String receiverId) {
        String sql = "INSERT INTO chat_requests (sender_id, receiver_id) VALUES (?::uuid, ?::uuid)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            p.setString(2, receiverId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ sendChatRequestById (already sent?): " + e.getMessage());
            return false;
        }
    }

    public List<com.scholar.model.PrivateContact> getAcceptedContacts() {
        List<com.scholar.model.PrivateContact> list = new ArrayList<>();
        String sql = """
                SELECT u.id, COALESCE(u.username, 'Unknown') AS username, p.profile_picture_url
                FROM chat_requests r
                JOIN users u    ON (r.sender_id = u.id OR r.receiver_id = u.id)
                LEFT JOIN profiles p ON u.id = p.user_id
                WHERE (r.sender_id = ?::uuid OR r.receiver_id = ?::uuid)
                  AND u.id != ?::uuid
                  AND r.status = 'ACCEPTED'
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            UUID me = requireUid();
            p.setObject(1, me); p.setObject(2, me); p.setObject(3, me);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next())
                    list.add(new com.scholar.model.PrivateContact(
                            rs.getString("id"), rs.getString("username"),
                            rs.getString("profile_picture_url")));
            }
        } catch (SQLException e) {
            System.err.println("❌ getAcceptedContacts: " + e.getMessage());
        }
        return list;
    }

    public List<com.scholar.model.PrivateContact> getChannelMembers(int channelId) {
        List<com.scholar.model.PrivateContact> list = new ArrayList<>();
        String sql = """
                SELECT u.id, COALESCE(u.username, 'Unknown') AS username, p.profile_picture_url
                FROM channel_members cm
                JOIN users u    ON cm.user_id = u.id
                LEFT JOIN profiles p ON u.id = p.user_id
                WHERE cm.channel_id = ? AND cm.status = 'approved' AND u.id != ?::uuid
                ORDER BY u.username
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, channelId);
            p.setObject(2, requireUid());
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next())
                    list.add(new com.scholar.model.PrivateContact(
                            rs.getString("id"), rs.getString("username"),
                            rs.getString("profile_picture_url")));
            }
        } catch (SQLException e) {
            System.err.println("❌ getChannelMembers: " + e.getMessage());
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────
    //  PROFILE — for popup display
    // ─────────────────────────────────────────────────────────

    /** Load a user's public-facing profile (used by the profile popup). */
    public com.scholar.model.Profile getProfileForUser(String userId) {
        try {
            return profileService.getUserProfile(java.util.UUID.fromString(userId));
        } catch (Exception e) {
            System.err.println("❌ getProfileForUser: " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  POLLS
    // ─────────────────────────────────────────────────────────

    public boolean createPoll(String question, List<String> options) {
        if (question == null || question.isBlank() || options == null || options.size() < 2) return false;
        String insertPoll = "INSERT INTO channel_polls (channel_id, creator_id, question) VALUES (?, ?::uuid, ?) RETURNING id";
        String insertOpt  = "INSERT INTO poll_options (poll_id, option_text) VALUES (?::uuid, ?)";

        try (Connection conn = dataService.connect()) {
            conn.setAutoCommit(false);
            try {
                String pollId;
                try (PreparedStatement ps = conn.prepareStatement(insertPoll)) {
                    ps.setInt(1, AuthService.CURRENT_CHANNEL_ID);
                    ps.setObject(2, requireUid());
                    ps.setString(3, question.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        pollId = rs.getString("id");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(insertOpt)) {
                    for (String opt : options) {
                        if (opt == null || opt.isBlank()) continue;
                        ps.setString(1, pollId);
                        ps.setString(2, opt.trim());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ createPoll: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("❌ createPoll connection: " + e.getMessage());
            return false;
        }
    }

    public List<com.scholar.model.Poll> getChannelPolls() {
        List<com.scholar.model.Poll> polls = new ArrayList<>();
        String pollSql = """
                SELECT p.id, p.question, COALESCE(u.username, 'Unknown') AS username
                FROM channel_polls p
                JOIN users u ON p.creator_id = u.id
                WHERE p.channel_id = ?
                ORDER BY p.created_at DESC
                """;
        String optSql = """
                SELECT o.id, o.option_text,
                  (SELECT COUNT(*) FROM poll_votes v WHERE v.option_id = o.id) AS votes,
                  (SELECT COUNT(*) FROM poll_votes v WHERE v.option_id = o.id AND v.user_id = ?::uuid) AS my_vote
                FROM poll_options o
                WHERE o.poll_id = ?::uuid
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement psPoll = conn.prepareStatement(pollSql)) {
            psPoll.setInt(1, AuthService.CURRENT_CHANNEL_ID);
            try (ResultSet rsPoll = psPoll.executeQuery()) {
                while (rsPoll.next()) {
                    String pollId = rsPoll.getString("id");
                    List<com.scholar.model.PollOption> optList = new ArrayList<>();
                    int totalVotes = 0;
                    try (PreparedStatement psOpt = conn.prepareStatement(optSql)) {
                        psOpt.setObject(1, requireUid());
                        psOpt.setString(2, pollId);
                        try (ResultSet rsOpt = psOpt.executeQuery()) {
                            while (rsOpt.next()) {
                                int votes = rsOpt.getInt("votes");
                                totalVotes += votes;
                                optList.add(new com.scholar.model.PollOption(
                                        rsOpt.getString("id"), rsOpt.getString("option_text"),
                                        votes, rsOpt.getInt("my_vote") > 0));
                            }
                        }
                    }
                    polls.add(new com.scholar.model.Poll(
                            pollId, rsPoll.getString("question"),
                            rsPoll.getString("username"), optList, totalVotes));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ getChannelPolls: " + e.getMessage());
        }
        return polls;
    }

    public boolean castVote(String pollId, String optionId) {
        String sql = """
                INSERT INTO poll_votes (poll_id, option_id, user_id) VALUES (?::uuid, ?::uuid, ?::uuid)
                ON CONFLICT (poll_id, user_id) DO UPDATE SET option_id = EXCLUDED.option_id
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, pollId); p.setString(2, optionId);
            p.setObject(3, requireUid());
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
                        "DELETE FROM poll_votes   WHERE poll_id = ?::uuid",
                        "DELETE FROM poll_options WHERE poll_id = ?::uuid",
                        "DELETE FROM channel_polls WHERE id    = ?::uuid"}) {
                    try (PreparedStatement p = conn.prepareStatement(sql)) {
                        p.setString(1, pollId); p.executeUpdate();
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
        if (optionText == null || optionText.isBlank()) return false;
        String sql = "INSERT INTO poll_options (poll_id, option_text) VALUES (?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, pollId); p.setString(2, optionText.trim());
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ addPollOption: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  DAILY THREADS
    // ─────────────────────────────────────────────────────────

    public String createDailyThread(String content, String mediaUrl, String photoUrl) {
        return createDailyThread(content, mediaUrl, photoUrl, "PUBLIC");
    }

    public String createDailyThread(String content, String mediaUrl, String photoUrl, String category) {
        String sql = """
                INSERT INTO daily_threads (user_id, content_text, media_url, photo_url, category)
                VALUES (?::uuid, ?, ?, ?, ?)
                """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            p.setString(2, content  != null ? content  : "");
            p.setString(3, mediaUrl != null ? mediaUrl : "");
            p.setString(4, photoUrl != null ? photoUrl : "");
            p.setString(5, category != null ? category.toUpperCase() : "PUBLIC");
            p.executeUpdate();
            return "SUCCESS";
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) return "ALREADY_POSTED";
            System.err.println("❌ createDailyThread: " + e.getMessage());
            return "ERROR";
        }
    }

    /**
     * Fetches threads by category.
     * All parameters are bound safely — no string injection.
     */
    public List<com.scholar.model.DailyThread> getThreadsByCategory(String category) {
        List<com.scholar.model.DailyThread> threads = new ArrayList<>();
        String upperCat = category != null ? category.toUpperCase() : "HOME";
        UUID   uid      = requireUid();

        // Reaction sub-selects use ? — no interpolation
        String reactionCols = """
                , (SELECT COUNT(*) FROM thread_likes    l WHERE l.thread_id=t.id AND l.user_id=?::uuid) AS liked_by_me
                , (SELECT COUNT(*) FROM thread_dislikes d WHERE d.thread_id=t.id AND d.user_id=?::uuid) AS disliked_by_me
                , (SELECT COUNT(*) FROM thread_saves    s WHERE s.thread_id=t.id AND s.user_id=?::uuid) AS saved_by_me
                """;

        String base = """
                SELECT t.id, t.user_id, u.username AS author_name, p.profile_picture_url AS author_avatar,
                       t.content_text, t.media_url, t.photo_url, t.category,
                       COALESCE(t.like_count,0) AS like_count, COALESCE(t.dislike_count,0) AS dislike_count,
                       t.created_at
                """ + reactionCols + """
                FROM daily_threads t
                JOIN  users u    ON t.user_id = u.id
                LEFT JOIN profiles p ON t.user_id = p.user_id
                """;

        String sql = switch (upperCat) {
            case "HOME"       -> base + "WHERE t.created_at >= NOW() - INTERVAL '24 hours' ORDER BY t.created_at DESC LIMIT 100";
            case "MY_THREADS" -> base + "WHERE t.user_id = ?::uuid AND t.created_at >= NOW() - INTERVAL '24 hours' ORDER BY t.created_at DESC";
            case "SAVED"      -> base + "JOIN thread_saves sv ON sv.thread_id=t.id WHERE sv.user_id=?::uuid ORDER BY sv.saved_at DESC";
            default           -> base + "WHERE UPPER(t.category)=? AND t.created_at >= NOW() - INTERVAL '24 hours' ORDER BY t.created_at DESC LIMIT 100";
        };

        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {

            // First 3 params are always the reaction sub-select UUIDs
            p.setObject(1, uid); p.setObject(2, uid); p.setObject(3, uid);

            // 4th param is the category filter (if needed)
            switch (upperCat) {
                case "MY_THREADS", "SAVED" -> p.setObject(4, uid);
                case "HOME"                -> { /* no 4th param */ }
                default                    -> p.setString(4, upperCat);
            }

            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    threads.add(new com.scholar.model.DailyThread(
                            rs.getString("id"),         rs.getString("user_id"),
                            rs.getString("author_name"),rs.getString("author_avatar"),
                            rs.getString("content_text"),rs.getString("media_url"),
                            rs.getString("photo_url"),  rs.getString("category"),
                            rs.getInt("like_count"),    rs.getInt("dislike_count"),
                            rs.getInt("liked_by_me") > 0,
                            rs.getInt("disliked_by_me") > 0,
                            rs.getInt("saved_by_me") > 0,
                            formatTimeAgo(rs.getString("created_at"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ getThreadsByCategory (" + upperCat + "): " + e.getMessage());
        }
        return threads;
    }

    // ─────────────────────────────────────────────────────────
    //  REACTIONS (LIKE / DISLIKE — MUTUALLY EXCLUSIVE)
    // ─────────────────────────────────────────────────────────

    public boolean likeThread   (String threadId) { return toggleReaction(threadId, "thread_likes",    "thread_dislikes"); }
    public boolean dislikeThread(String threadId) { return toggleReaction(threadId, "thread_dislikes", "thread_likes"); }

    private boolean toggleReaction(String threadId, String targetTable, String oppositeTable) {
        // Both tables are internal constants — no user data in table names, safe to format
        String delOpposite  = "DELETE FROM " + oppositeTable + " WHERE thread_id=?::uuid AND user_id=?::uuid";
        String checkTarget  = "SELECT 1 FROM " + targetTable + " WHERE thread_id=?::uuid AND user_id=?::uuid";
        String insTarget    = "INSERT INTO " + targetTable + " (thread_id, user_id) VALUES (?::uuid, ?::uuid)";
        String delTarget    = "DELETE FROM " + targetTable + " WHERE thread_id=?::uuid AND user_id=?::uuid";
        String updateCounts = """
                UPDATE daily_threads SET
                  like_count    = (SELECT COUNT(*) FROM thread_likes    WHERE thread_id=?::uuid),
                  dislike_count = (SELECT COUNT(*) FROM thread_dislikes WHERE thread_id=?::uuid)
                WHERE id = ?::uuid
                """;
        UUID uid = requireUid();
        try (Connection conn = dataService.connect()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement p1 = conn.prepareStatement(delOpposite)) {
                    p1.setString(1, threadId); p1.setObject(2, uid); p1.executeUpdate();
                }
                boolean exists;
                try (PreparedStatement pCheck = conn.prepareStatement(checkTarget)) {
                    pCheck.setString(1, threadId); pCheck.setObject(2, uid);
                    try (ResultSet rs = pCheck.executeQuery()) { exists = rs.next(); }
                }
                if (exists) {
                    try (PreparedStatement pDel = conn.prepareStatement(delTarget)) {
                        pDel.setString(1, threadId); pDel.setObject(2, uid); pDel.executeUpdate();
                    }
                } else {
                    try (PreparedStatement pIns = conn.prepareStatement(insTarget)) {
                        pIns.setString(1, threadId); pIns.setObject(2, uid); pIns.executeUpdate();
                    }
                }
                try (PreparedStatement pUpd = conn.prepareStatement(updateCounts)) {
                    pUpd.setString(1, threadId); pUpd.setString(2, threadId); pUpd.setString(3, threadId);
                    pUpd.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ toggleReaction: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) { return false; }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE / UNSAVE THREAD
    // ─────────────────────────────────────────────────────────

    public boolean saveThread(String threadId) {
        UUID uid = requireUid();
        String checkSql = "SELECT 1 FROM thread_saves WHERE thread_id=?::uuid AND user_id=?::uuid";
        String insSql   = "INSERT INTO thread_saves (thread_id, user_id) VALUES (?::uuid, ?::uuid)";
        String delSql   = "DELETE FROM thread_saves WHERE thread_id=?::uuid AND user_id=?::uuid";
        try (Connection conn = dataService.connect()) {
            boolean saved;
            try (PreparedStatement pCheck = conn.prepareStatement(checkSql)) {
                pCheck.setString(1, threadId); pCheck.setObject(2, uid);
                try (ResultSet rs = pCheck.executeQuery()) { saved = rs.next(); }
            }
            String sql = saved ? delSql : insSql;
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, threadId); p.setObject(2, uid); p.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveThread: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  NOTIFICATIONS
    // ─────────────────────────────────────────────────────────

    public List<AppNotification> getNotifications() {
        List<AppNotification> list = new ArrayList<>();
        String sql = "SELECT * FROM app_notifications WHERE user_id=?::uuid ORDER BY created_at DESC LIMIT 50";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next())
                    list.add(new AppNotification(
                            rs.getString("id"), rs.getString("type"),
                            rs.getString("title"), rs.getString("body"),
                            rs.getBoolean("is_read"), formatTimeAgo(rs.getString("created_at"))));
            }
        } catch (SQLException e) {
            System.err.println("❌ getNotifications: " + e.getMessage());
        }
        return list;
    }

    public int getUnreadNotificationCount() {
        String sql = "SELECT COUNT(*) FROM app_notifications WHERE user_id=?::uuid AND is_read=false";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid());
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ getUnreadNotificationCount: " + e.getMessage());
        }
        return 0;
    }

    public boolean markNotificationRead(String notifId) {
        String sql = "UPDATE app_notifications SET is_read=true WHERE id=?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, notifId);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ markNotificationRead: " + e.getMessage());
            return false;
        }
    }

    public void markAllNotificationsRead() {
        String sql = "UPDATE app_notifications SET is_read=true WHERE user_id=?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setObject(1, requireUid()); p.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ markAllNotificationsRead: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────

    public String formatTimeAgo(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            String iso = createdAt.replace(" ", "T");
            if (!iso.contains("+") && !iso.endsWith("Z")) iso += "Z";
            OffsetDateTime dt   = OffsetDateTime.parse(iso);
            long           mins = Duration.between(dt, OffsetDateTime.now()).toMinutes();
            if (mins <  1)    return "just now";
            if (mins < 60)    return mins + "m ago";
            if (mins < 1440)  return (mins / 60) + "h ago";
            return (mins / 1440) + "d ago";
        } catch (Exception e) {
            return createdAt.substring(0, Math.min(10, createdAt.length()));
        }
    }
}