package com.scholar.service;

import com.scholar.model.AnswerReply;
import com.scholar.model.Doubt;
import com.scholar.model.DoubtAnswer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConsultancyService {

    @Autowired private DataService dataService;

    /**
     * FIX: Original SQL had 6 positional '?' but 7 pstmt.set*() calls.
     * "topic" was missing from the VALUES list — added "?" for it now.
     * Param order: title, description, subject, topic, privacy, student_id, is_anonymous
     */
    public boolean submitDoubt(String title, String desc, String subject, String topic,
                               String privacy, boolean isAnonymous) {
        String sql = "INSERT INTO academic_doubts "
                   + "(title, description, subject, topic, privacy, student_id, is_anonymous) "
                   + "VALUES (?, ?, ?, ?, ?, ?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, desc);
            pstmt.setString(3, subject);
            pstmt.setString(4, topic != null ? topic : "");   // FIX: was missing
            pstmt.setString(5, privacy);
            pstmt.setObject(6, AuthService.CURRENT_USER_ID);
            pstmt.setBoolean(7, isAnonymous);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Returns all doubts with author name and profile picture. */
    public List<Doubt> getAllOpenDoubts() {
        List<Doubt> list = new ArrayList<>();
        String sql = "SELECT d.*, u.username, p.profile_picture_url "
                   + "FROM academic_doubts d "
                   + "JOIN users u ON d.student_id = u.id "
                   + "LEFT JOIN profiles p ON u.id = p.user_id "
                   + "ORDER BY d.created_at DESC";
        try (Connection conn = dataService.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Doubt(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("subject"),
                    rs.getString("topic"),
                    rs.getString("privacy"),
                    rs.getString("status"),
                    rs.getString("username"),
                    rs.getString("student_id"),
                    rs.getString("created_at"),
                    rs.getBoolean("is_anonymous"),
                    rs.getString("profile_picture_url")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Returns answers for a doubt, each with nested replies and gamification count. */
    public List<DoubtAnswer> getAnswers(String doubtId) {
        List<DoubtAnswer> list = new ArrayList<>();
        String sql = "SELECT a.*, u.username, p.profile_picture_url, "
                   + "(SELECT COUNT(*) FROM doubt_answers da "
                   + " WHERE da.mentor_id = a.mentor_id AND da.is_best_answer = TRUE) AS best_answer_count "
                   + "FROM doubt_answers a "
                   + "JOIN users u ON a.mentor_id = u.id "
                   + "LEFT JOIN profiles p ON u.id = p.user_id "
                   + "WHERE a.doubt_id = ?::uuid "
                   + "ORDER BY a.is_best_answer DESC, a.upvotes DESC, a.created_at ASC";

        try (Connection conn = dataService.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, doubtId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String answerId = rs.getString("id");
                    List<AnswerReply> replies = getAnswerReplies(answerId, conn);
                    list.add(new DoubtAnswer(
                        answerId,
                        rs.getString("doubt_id"),
                        rs.getString("username"),
                        rs.getString("mentor_id"),
                        rs.getString("content"),
                        rs.getBoolean("is_best_answer"),
                        rs.getInt("upvotes"),
                        rs.getString("created_at"),
                        rs.getInt("best_answer_count"),
                        replies,
                        rs.getString("profile_picture_url")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private List<AnswerReply> getAnswerReplies(String answerId, Connection conn) {
        List<AnswerReply> replies = new ArrayList<>();
        String sql = "SELECT r.*, u.username, p.profile_picture_url "
                   + "FROM answer_replies r "
                   + "JOIN users u ON r.user_id = u.id "
                   + "LEFT JOIN profiles p ON u.id = p.user_id "
                   + "WHERE r.answer_id = ?::uuid "
                   + "ORDER BY r.created_at ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, answerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    replies.add(new AnswerReply(
                        rs.getString("id"),
                        rs.getString("answer_id"),
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("content"),
                        rs.getString("created_at"),
                        rs.getString("profile_picture_url")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return replies;
    }

    public boolean submitAnswer(String doubtId, String content) {
        String sql = "INSERT INTO doubt_answers (doubt_id, mentor_id, content) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, doubtId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            pstmt.setString(3, content);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean submitAnswerReply(String answerId, String content) {
        String sql = "INSERT INTO answer_replies (answer_id, user_id, content) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection conn = dataService.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, answerId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            pstmt.setString(3, content);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Marks one answer as best and resolves the doubt — wrapped in a transaction. */
    public boolean markBestAnswer(String doubtId, String answerId) {
        String clearBest  = "UPDATE doubt_answers SET is_best_answer = FALSE WHERE doubt_id = ?::uuid";
        String setBest    = "UPDATE doubt_answers SET is_best_answer = TRUE  WHERE id = ?::uuid";
        String resolveQ   = "UPDATE academic_doubts SET status = 'RESOLVED' WHERE id = ?::uuid";
        try (Connection conn = dataService.connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement(clearBest);
                 PreparedStatement p2 = conn.prepareStatement(setBest);
                 PreparedStatement p3 = conn.prepareStatement(resolveQ)) {
                p1.setString(1, doubtId);  p1.executeUpdate();
                p2.setString(1, answerId); p2.executeUpdate();
                p3.setString(1, doubtId);  p3.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteDoubt(String doubtId) {
        String sql = "DELETE FROM academic_doubts WHERE id = ?::uuid";
        try (Connection conn = dataService.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, doubtId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}