package com.scholar.service;

import com.scholar.model.StudyHistory;
import com.scholar.model.StudyRoom;
import com.scholar.model.StudySession;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class StudyRoomService {

    // ══════════════════════════════════════════════════════════
    // ROOM CRUD (Only PUBLIC & PRIVATE)
    // ══════════════════════════════════════════════════════════

    public List<StudyRoom> getPublicRooms() {
        List<StudyRoom> rooms = new ArrayList<>();
        // Fetch ONLY PUBLIC rooms
        String sql = "SELECT r.*, u.username as creator_name, " +
                     "(SELECT COUNT(*) FROM room_participants p WHERE p.room_id = r.id AND p.status='STUDYING') as active_users " +
                     "FROM study_rooms r LEFT JOIN users u ON r.created_by = u.id " +
                     "WHERE r.type = 'PUBLIC' AND r.active_status = TRUE ORDER BY r.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rooms.add(mapRoom(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return rooms;
    }

    public StudyRoom getMyPrivateRoom(String userId) {
        String sql = "SELECT r.*, u.username as creator_name, " +
                     "(SELECT COUNT(*) FROM room_participants p WHERE p.room_id = r.id AND p.status='STUDYING') as active_users " +
                     "FROM study_rooms r LEFT JOIN users u ON r.created_by = u.id " +
                     "WHERE r.type='PRIVATE' AND r.created_by=?::uuid LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRoom(rs);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public String createRoom(String roomName, String type, String createdBy, String mode) {
        // Department is set to empty string "" to avoid DB schema conflict
        String sql = "INSERT INTO study_rooms (room_name, type, department, created_by, mode) " +
                     "VALUES (?, ?, '', ?::uuid, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomName);
            ps.setString(2, type);
            ps.setString(3, createdBy);
            ps.setString(4, mode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean toggleMode(String roomId, String newMode) {
        String sql = "UPDATE study_rooms SET mode=? WHERE id=?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newMode);
            ps.setString(2, roomId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ══════════════════════════════════════════════════════════
    // PARTICIPANT JOIN / LEAVE
    // ══════════════════════════════════════════════════════════

    public String joinRoom(String roomId, String userId, String userName, String topic, String task, int timerMinutes) {
        leaveRoom(roomId, userId); // Clear stale sessions
        String sql = "INSERT INTO room_participants " +
                     "(room_id, user_id, topic, task_description, timer_duration, status) " +
                     "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'STUDYING') RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setString(2, userId);
            ps.setString(3, topic);
            ps.setString(4, task);
            ps.setInt(5, timerMinutes);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1); // Returns UUID
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean completeSession(String participantId, int actualMinutes) {
        String sql = "UPDATE room_participants SET status='COMPLETED', actual_study_time=? WHERE id=?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actualMinutes);
            ps.setString(2, participantId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean leaveRoom(String roomId, String userId) {
        String sql = "UPDATE room_participants SET status='ABANDONED' " +
                     "WHERE room_id=?::uuid AND user_id=?::uuid AND status='STUDYING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<StudySession> getAllSessions(String roomId) {
        List<StudySession> sessions = new ArrayList<>();
        String sql = "SELECT p.*, u.username as user_name FROM room_participants p " +
                     "JOIN users u ON p.user_id = u.id " +
                     "WHERE p.room_id=?::uuid ORDER BY p.start_time DESC LIMIT 50";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) sessions.add(mapSession(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return sessions;
    }

    // ══════════════════════════════════════════════════════════
    // STUDY HISTORY & STATS
    // ══════════════════════════════════════════════════════════

    public boolean saveHistory(String userId, String roomId, String topic, String task, int planned, int completed, int xp) {
        String sql = "INSERT INTO study_history (user_id, room_id, topic, task, planned_time, completed_time, earned_xp) " +
                     "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, roomId);
            ps.setString(3, topic);
            ps.setString(4, task);
            ps.setInt(5, planned);
            ps.setInt(6, completed);
            ps.setInt(7, xp);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<StudyHistory> getHistory(String userId) {
        List<StudyHistory> list = new ArrayList<>();
        String sql = "SELECT * FROM study_history WHERE user_id=?::uuid ORDER BY created_at DESC LIMIT 100";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapHistory(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public int getTodayStudyMinutes(String userId) {
        String sql = "SELECT COALESCE(SUM(completed_time),0) FROM study_history " +
                     "WHERE user_id=?::uuid AND DATE(created_at) = CURRENT_DATE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public java.util.Map<String, Integer> getWeeklyStats(String userId) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT to_char(created_at,'Dy') as day_name, COALESCE(SUM(completed_time),0) as mins " +
                     "FROM study_history WHERE user_id=?::uuid AND created_at >= CURRENT_DATE - INTERVAL '6 days' " +
                     "GROUP BY DATE(created_at), day_name ORDER BY DATE(created_at) ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("day_name"), rs.getInt("mins"));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public java.util.Map<String, Integer> getTopicStats(String userId) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT topic, SUM(completed_time) as mins FROM study_history " +
                     "WHERE user_id=?::uuid GROUP BY topic ORDER BY mins DESC LIMIT 8";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("topic"), rs.getInt("mins"));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    // ══════════════════════════════════════════════════════════
    // XP & STREAK & GLOBAL LEADERBOARD
    // ══════════════════════════════════════════════════════════

    public void updateStreak(String userId) {
        int todayMins = getTodayStudyMinutes(userId);
        if (todayMins >= 30) {
            String updateSql = "UPDATE users SET study_streak = COALESCE(study_streak, 0) + 1 " +
                               "WHERE id=?::uuid AND (last_streak_date IS NULL OR last_streak_date < CURRENT_DATE)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ups = conn.prepareStatement(updateSql)) {
                ups.setString(1, userId);
                ups.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public boolean addXP(String userId, int xp) {
        String sql = "UPDATE users SET total_xp = COALESCE(total_xp,0) + ? WHERE id=?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, xp);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int getStreak(String userId) {
        String sql = "SELECT COALESCE(study_streak, 0) FROM users WHERE id=?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<String[]> getGlobalLeaderboard() {
        List<String[]> board = new ArrayList<>();
        // Removed Department - this is now a Global Leaderboard
        String sql = "SELECT username, COALESCE(total_xp,0) as xp, COALESCE(study_streak,0) as streak " +
                     "FROM users ORDER BY xp DESC LIMIT 10";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                board.add(new String[]{rs.getString("username"), String.valueOf(rs.getInt("xp")), String.valueOf(rs.getInt("streak"))});
        } catch (SQLException e) { e.printStackTrace(); }
        return board;
    }

    // ══════════════════════════════════════════════════════════
    // CHALLENGES & CHAT
    // ══════════════════════════════════════════════════════════

    public boolean saveChallenge(String roomId, String meetLink, String name, String createdBy) {
        String sql = "INSERT INTO study_challenges (room_id, meet_link, challenge_name, created_by) VALUES (?::uuid, ?, ?, ?::uuid)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId); ps.setString(2, meetLink);
            ps.setString(3, name);   ps.setString(4, createdBy);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String getChallengeMeetLink(String roomId) {
        String sql = "SELECT meet_link FROM study_challenges WHERE room_id=?::uuid ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public record ChatMessage(String sender, String content, String time) {}

    public boolean sendChatMessage(String roomId, String userId, String userName, String content) {
        String sql = "INSERT INTO study_room_chat (room_id, user_id, user_name, content) VALUES (?::uuid, ?::uuid, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId); ps.setString(2, userId);
            ps.setString(3, userName); ps.setString(4, content);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<ChatMessage> getChatMessages(String roomId) {
        List<ChatMessage> msgs = new ArrayList<>();
        String sql = "SELECT user_name, content, created_at FROM study_room_chat WHERE room_id=?::uuid ORDER BY created_at DESC LIMIT 80";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) msgs.add(0, new ChatMessage(rs.getString("user_name"), rs.getString("content"), rs.getString("created_at")));
            return msgs;
        } catch (SQLException e) { e.printStackTrace(); }
        return msgs;
    }

    // ══════════════════════════════════════════════════════════
    // MAPPERS
    // ══════════════════════════════════════════════════════════

    private StudyRoom mapRoom(ResultSet rs) throws SQLException {
        return new StudyRoom(
            rs.getString("id"), rs.getString("room_name"), rs.getString("type"),
            rs.getString("department"), rs.getString("created_by"), rs.getString("creator_name"),
            rs.getString("mode"), rs.getBoolean("active_status"), rs.getInt("active_users")
        );
    }

    private StudySession mapSession(ResultSet rs) throws SQLException {
        return new StudySession(
            rs.getString("id"), rs.getString("room_id"), rs.getString("user_id"),
            rs.getString("user_name"), rs.getString("topic"), rs.getString("task_description"),
            rs.getInt("timer_duration"), rs.getString("start_time"), rs.getString("status")
        );
    }

    private StudyHistory mapHistory(ResultSet rs) throws SQLException {
        return new StudyHistory(
            rs.getString("id"), rs.getString("user_id"), rs.getString("topic"),
            rs.getString("task"), rs.getInt("planned_time"), rs.getInt("completed_time"),
            rs.getInt("earned_xp"), rs.getString("created_at")
        );
    }
}