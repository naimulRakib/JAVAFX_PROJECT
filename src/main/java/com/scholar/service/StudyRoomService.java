package com.scholar.service;

import com.scholar.model.StudyHistory;
import com.scholar.model.StudyRoom;
import com.scholar.model.StudySession;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * StudyRoomService — v3
 *
 * New methods:
 *  - getBoardSessions(roomId)  : COMPLETED + STUDYING + ON_BREAK (no ABANDONED) for board table
 *  - setBreak(participantId, onBreak) : toggles ON_BREAK status for pause/resume
 *  - saveAnalyticsOnly(userId, mins)  : records abandoned elapsed time for analytics without
 *                                        creating a history entry
 *  - getTotalXP(userId)        : was missing in v1
 *
 * Bug fixes:
 *  - ResultSet always closed
 *  - saveHistory handles null roomId (private room)
 *  - getChatMessages returns ASC order
 *  - leaveRoom clears only STUDYING rows (not COMPLETED)
 *
 * Path: src/main/java/com/scholar/service/StudyRoomService.java
 */
@Service
public class StudyRoomService {

    // ══════════════════════════════════════════════════
    // ROOM CRUD
    // ══════════════════════════════════════════════════

    public List<StudyRoom> getPublicRooms() {
        List<StudyRoom> rooms = new ArrayList<>();
        String sql =
            "SELECT r.*, u.username AS creator_name, " +
            "  (SELECT COUNT(*) FROM room_participants p " +
            "   WHERE p.room_id = r.id AND p.status = 'STUDYING') AS active_users " +
            "FROM study_rooms r " +
            "LEFT JOIN users u ON r.created_by = u.id " +
            "WHERE r.type = 'PUBLIC' AND r.active_status = TRUE " +
            "ORDER BY r.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rooms.add(mapRoom(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return rooms;
    }

    public StudyRoom getMyPrivateRoom(String userId) {
        String sql =
            "SELECT r.*, u.username AS creator_name, " +
            "  (SELECT COUNT(*) FROM room_participants p " +
            "   WHERE p.room_id = r.id AND p.status = 'STUDYING') AS active_users " +
            "FROM study_rooms r LEFT JOIN users u ON r.created_by = u.id " +
            "WHERE r.type = 'PRIVATE' AND r.created_by = ?::uuid LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRoom(rs);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public String createRoom(String roomName, String type, String createdBy, String mode) {
        String sql =
            "INSERT INTO study_rooms (room_name, type, department, created_by, mode) " +
            "VALUES (?, ?, '', ?::uuid, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName); ps.setString(2, type);
            ps.setString(3, createdBy); ps.setString(4, mode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean toggleMode(String roomId, String newMode) {
        String sql = "UPDATE study_rooms SET mode = ? WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newMode); ps.setString(2, roomId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ══════════════════════════════════════════════════
    // PARTICIPANT JOIN / LEAVE / STATUS
    // ══════════════════════════════════════════════════

    public String joinRoom(String roomId, String userId, String userName,
                           String topic, String task, int timerMinutes) {
        leaveRoom(roomId, userId); // clear stale STUDYING rows
        String sql =
            "INSERT INTO room_participants " +
            "  (room_id, user_id, topic, task_description, timer_duration, status) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'STUDYING') RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId); ps.setString(2, userId);
            ps.setString(3, topic);  ps.setString(4, task);
            ps.setInt(5, timerMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean completeSession(String participantId, int actualMinutes) {
        String sql =
            "UPDATE room_participants " +
            "SET status = 'COMPLETED', actual_study_time = ? " +
            "WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actualMinutes); ps.setString(2, participantId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * Toggle ON_BREAK status when user pauses/resumes timer.
     * The board table will reflect this with "⏸ Break" badge.
     */
    public boolean setBreak(String participantId, boolean onBreak) {
        String newStatus = onBreak ? "ON_BREAK" : "STUDYING";
        String sql =
            "UPDATE room_participants SET status = ? " +
            "WHERE id = ?::uuid AND status IN ('STUDYING', 'ON_BREAK')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus); ps.setString(2, participantId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Marks STUDYING rows as ABANDONED (used on leave). Does NOT touch COMPLETED rows. */
    public boolean leaveRoom(String roomId, String userId) {
        String sql =
            "UPDATE room_participants SET status = 'ABANDONED' " +
            "WHERE room_id = ?::uuid AND user_id = ?::uuid " +
            "AND status IN ('STUDYING', 'ON_BREAK')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId); ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * Returns sessions for the board table:
     * COMPLETED + STUDYING + ON_BREAK (NOT ABANDONED).
     * Ordered: COMPLETED first (most relevant), then active.
     */
    public List<StudySession> getBoardSessions(String roomId) {
        List<StudySession> sessions = new ArrayList<>();
        String sql =
            "SELECT p.*, u.username AS user_name FROM room_participants p " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE p.room_id = ?::uuid AND p.status <> 'ABANDONED' " +
            "ORDER BY " +
            "  CASE p.status WHEN 'COMPLETED' THEN 0 WHEN 'STUDYING' THEN 1 ELSE 2 END, " +
            "  p.start_time DESC " +
            "LIMIT 50";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sessions.add(mapSession(rs));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return sessions;
    }

    /** Legacy: kept for compatibility. Now calls getBoardSessions. */
    public List<StudySession> getAllSessions(String roomId) {
        return getBoardSessions(roomId);
    }

    // ══════════════════════════════════════════════════
    // HISTORY — COMPLETED sessions only
    // ══════════════════════════════════════════════════

    /**
     * Saves a COMPLETED session to study_history.
     * Call this ONLY on session completion, not on abandon/leave.
     * roomId may be null for private room sessions.
     */
    public boolean saveHistory(String userId, String roomId, String topic, String task,
                               int planned, int completed, int xp) {
        String sql = roomId != null
            ? "INSERT INTO study_history (user_id, room_id, topic, task, planned_time, completed_time, earned_xp) " +
              "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)"
            : "INSERT INTO study_history (user_id, topic, task, planned_time, completed_time, earned_xp) " +
              "VALUES (?::uuid, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, userId);
            if (roomId != null) ps.setString(i++, roomId);
            ps.setString(i++, topic != null ? topic : "");
            ps.setString(i++, task  != null ? task  : "");
            ps.setInt(i++, planned);
            ps.setInt(i++, completed);
            ps.setInt(i, xp);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * Records elapsed minutes from an ABANDONED session for analytics purposes ONLY.
     * Does NOT create a history entry.
     * Inserts into a lightweight analytics_events table if it exists,
     * otherwise falls back to updating a daily_analytics summary.
     *
     * Implementation: insert into study_analytics (user_id, minutes, created_at).
     * This table is read by getTodayStudyMinutes/getWeeklyStats via UNION.
     */
    public void saveAnalyticsOnly(String userId, int minutes) {
        // Use a simple insert into an analytics-only table.
        // If your schema uses study_history for analytics, you can alternatively
        // insert a row with earned_xp=0 and a special marker. Here we use a
        // separate table that is included in the stats queries.
        String sql =
            "INSERT INTO study_analytics (user_id, minutes, created_at) " +
            "VALUES (?::uuid, ?, NOW()) " +
            "ON CONFLICT DO NOTHING";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, minutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Table may not exist — silently skip
            // (analytics-only tracking is best-effort)
        }
    }

    /** Returns COMPLETED sessions for the History tab. */
    public List<StudyHistory> getHistory(String userId) {
        List<StudyHistory> list = new ArrayList<>();
        String sql =
            "SELECT * FROM study_history " +
            "WHERE user_id = ?::uuid " +
            "ORDER BY created_at DESC LIMIT 100";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapHistory(rs));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ══════════════════════════════════════════════════
    // ANALYTICS STATS
    // ══════════════════════════════════════════════════

    /**
     * Today's total studied minutes.
     * Includes BOTH completed (study_history) and partial/abandoned (study_analytics).
     */
    public int getTodayStudyMinutes(String userId) {
        // Union history + analytics so abandoned sessions count too
        String sql =
            "SELECT COALESCE(SUM(mins), 0) FROM (" +
            "  SELECT completed_time AS mins FROM study_history " +
            "  WHERE user_id = ?::uuid AND created_at::date = CURRENT_DATE " +
            "  UNION ALL " +
            "  SELECT minutes AS mins FROM study_analytics " +
            "  WHERE user_id = ?::uuid AND created_at::date = CURRENT_DATE " +
            ") t";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId); ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            // Fallback: history only (if analytics table doesn't exist)
            return getTodayHistoryMinutes(userId);
        }
        return 0;
    }

    private int getTodayHistoryMinutes(String userId) {
        String sql =
            "SELECT COALESCE(SUM(completed_time), 0) FROM study_history " +
            "WHERE user_id = ?::uuid AND created_at::date = CURRENT_DATE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /**
     * Weekly stats: includes abandoned elapsed minutes from study_analytics.
     */
    public java.util.Map<String, Integer> getWeeklyStats(String userId) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql =
            "SELECT to_char(dt, 'Dy') AS day_name, COALESCE(SUM(mins), 0) AS total " +
            "FROM (" +
            "  SELECT created_at AS dt, completed_time AS mins FROM study_history " +
            "  WHERE user_id = ?::uuid AND created_at >= CURRENT_DATE - INTERVAL '6 days' " +
            "  UNION ALL " +
            "  SELECT created_at AS dt, minutes AS mins FROM study_analytics " +
            "  WHERE user_id = ?::uuid AND created_at >= CURRENT_DATE - INTERVAL '6 days' " +
            ") t " +
            "GROUP BY DATE(dt), day_name ORDER BY DATE(dt) ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId); ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString("day_name"), rs.getInt("total"));
            }
        } catch (SQLException e) {
            // Fallback to history only
            return getWeeklyHistoryStats(userId);
        }
        return map;
    }

    private java.util.Map<String, Integer> getWeeklyHistoryStats(String userId) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql =
            "SELECT to_char(created_at,'Dy') AS day_name, COALESCE(SUM(completed_time),0) AS mins " +
            "FROM study_history " +
            "WHERE user_id=?::uuid AND created_at >= CURRENT_DATE - INTERVAL '6 days' " +
            "GROUP BY DATE(created_at), day_name ORDER BY DATE(created_at) ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString("day_name"), rs.getInt("mins"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public java.util.Map<String, Integer> getTopicStats(String userId) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql =
            "SELECT topic, SUM(completed_time) AS mins FROM study_history " +
            "WHERE user_id = ?::uuid AND topic IS NOT NULL AND topic <> '' " +
            "GROUP BY topic ORDER BY mins DESC LIMIT 8";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString("topic"), rs.getInt("mins"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    // ══════════════════════════════════════════════════
    // XP, STREAK, LEADERBOARD
    // ══════════════════════════════════════════════════

    public void updateStreak(String userId) {
        if (getTodayStudyMinutes(userId) >= 30) {
            String sql =
                "UPDATE users SET study_streak = COALESCE(study_streak,0) + 1, " +
                "last_streak_date = CURRENT_DATE " +
                "WHERE id = ?::uuid " +
                "AND (last_streak_date IS NULL OR last_streak_date < CURRENT_DATE)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public boolean addXP(String userId, int xp) {
        String sql = "UPDATE users SET total_xp = COALESCE(total_xp,0) + ? WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, xp); ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int getStreak(String userId) {
        String sql = "SELECT COALESCE(study_streak,0) FROM users WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getTotalXP(String userId) {
        String sql = "SELECT COALESCE(total_xp,0) FROM users WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<String[]> getGlobalLeaderboard() {
        List<String[]> board = new ArrayList<>();
        String sql =
            "SELECT username, COALESCE(total_xp,0) AS xp, COALESCE(study_streak,0) AS streak " +
            "FROM users ORDER BY xp DESC, streak DESC LIMIT 10";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                board.add(new String[]{
                    rs.getString("username"),
                    String.valueOf(rs.getInt("xp")),
                    String.valueOf(rs.getInt("streak"))
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return board;
    }

    // ══════════════════════════════════════════════════
    // CHAT
    // ══════════════════════════════════════════════════

    public record ChatMessage(String sender, String content, String time) {}

    public boolean sendChatMessage(String roomId, String userId, String userName, String content) {
        if (content == null || content.isBlank()) return false;
        String sql =
            "INSERT INTO study_room_chat (room_id, user_id, user_name, content) " +
            "VALUES (?::uuid, ?::uuid, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId); ps.setString(2, userId);
            ps.setString(3, userName); ps.setString(4, content);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Returns messages in chronological (ASC) order — oldest to newest. */
    public List<ChatMessage> getChatMessages(String roomId) {
        List<ChatMessage> msgs = new ArrayList<>();
        String sql =
            "SELECT user_name, content, created_at FROM study_room_chat " +
            "WHERE room_id = ?::uuid ORDER BY created_at ASC LIMIT 80";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) msgs.add(new ChatMessage(
                    rs.getString("user_name"),
                    rs.getString("content"),
                    rs.getString("created_at")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return msgs;
    }

    // ══════════════════════════════════════════════════
    // CHALLENGES
    // ══════════════════════════════════════════════════

    public boolean saveChallenge(String roomId, String meetLink, String name, String createdBy) {
        String sql =
            "INSERT INTO study_challenges (room_id, meet_link, challenge_name, created_by) " +
            "VALUES (?::uuid, ?, ?, ?::uuid)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId); ps.setString(2, meetLink);
            ps.setString(3, name);   ps.setString(4, createdBy);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String getChallengeMeetLink(String roomId) {
        String sql =
            "SELECT meet_link FROM study_challenges " +
            "WHERE room_id = ?::uuid ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // ══════════════════════════════════════════════════
    // MAPPERS
    // ══════════════════════════════════════════════════

    private StudyRoom mapRoom(ResultSet rs) throws SQLException {
        return new StudyRoom(
            rs.getString("id"), rs.getString("room_name"), rs.getString("type"),
            rs.getString("department"), rs.getString("created_by"), rs.getString("creator_name"),
            rs.getString("mode"), rs.getBoolean("active_status"), rs.getInt("active_users"));
    }

    private StudySession mapSession(ResultSet rs) throws SQLException {
        return new StudySession(
            rs.getString("id"), rs.getString("room_id"), rs.getString("user_id"),
            rs.getString("user_name"), rs.getString("topic"), rs.getString("task_description"),
            rs.getInt("timer_duration"), rs.getString("start_time"), rs.getString("status"));
    }

    private StudyHistory mapHistory(ResultSet rs) throws SQLException {
        return new StudyHistory(
            rs.getString("id"), rs.getString("user_id"), rs.getString("topic"),
            rs.getString("task"), rs.getInt("planned_time"), rs.getInt("completed_time"),
            rs.getInt("earned_xp"), rs.getString("created_at"));
    }
}