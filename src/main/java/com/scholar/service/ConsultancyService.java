package com.scholar.service;

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

    // ১. নতুন ডাউট সাবমিট করা
   public boolean submitDoubt(String title, String desc, String subject, String topic, String privacy, boolean isAnonymous) {
        String sql = "INSERT INTO academic_doubts (title, description, subject, topic, privacy, student_id, is_anonymous) VALUES (?, ?, ?, ?, ?, ?::uuid, ?)";
        try (Connection conn = dataService.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title); pstmt.setString(2, desc); pstmt.setString(3, subject);
            pstmt.setString(4, topic); pstmt.setString(5, privacy); pstmt.setObject(6, AuthService.CURRENT_USER_ID); pstmt.setBoolean(7, isAnonymous);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ২. ওপেন ডাউটগুলো নিয়ে আসা
    public List<Doubt> getAllOpenDoubts() {
        List<Doubt> list = new ArrayList<>();
        String sql = "SELECT d.*, u.username FROM academic_doubts d JOIN users u ON d.student_id = u.id ORDER BY d.created_at DESC";
        try (Connection conn = dataService.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Doubt(
                    rs.getString("id"), rs.getString("title"), rs.getString("description"),
                    rs.getString("subject"), rs.getString("topic"), rs.getString("privacy"),
                    rs.getString("status"), rs.getString("username"), rs.getString("student_id"), 
                    rs.getString("created_at"), rs.getBoolean("is_anonymous")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ৩. ডাউটের উত্তরগুলো আনা
    public List<DoubtAnswer> getAnswers(String doubtId) {
        List<DoubtAnswer> list = new ArrayList<>();
        String sql = "SELECT a.*, u.username FROM doubt_answers a JOIN users u ON a.mentor_id = u.id WHERE a.doubt_id = ?::uuid ORDER BY a.is_best_answer DESC, a.upvotes DESC, a.created_at ASC";
        try (Connection conn = dataService.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, doubtId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new DoubtAnswer(
                    rs.getString("id"), rs.getString("doubt_id"), rs.getString("username"),
                    rs.getString("mentor_id"), rs.getString("content"), rs.getBoolean("is_best_answer"),
                    rs.getInt("upvotes"), rs.getString("created_at")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ৪. উত্তর দেওয়া (Thread-based answer)
    public boolean submitAnswer(String doubtId, String content) {
        String sql = "INSERT INTO doubt_answers (doubt_id, mentor_id, content) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection conn = dataService.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, doubtId); pstmt.setObject(2, AuthService.CURRENT_USER_ID); pstmt.setString(3, content);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ৫. Best Answer মার্ক করা
    public boolean markBestAnswer(String doubtId, String answerId) {
        String sql1 = "UPDATE doubt_answers SET is_best_answer = FALSE WHERE doubt_id = ?::uuid";
        String sql2 = "UPDATE doubt_answers SET is_best_answer = TRUE WHERE id = ?::uuid";
        String sql3 = "UPDATE academic_doubts SET status = 'RESOLVED' WHERE id = ?::uuid";
        try (Connection conn = dataService.connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement(sql1); PreparedStatement p2 = conn.prepareStatement(sql2); PreparedStatement p3 = conn.prepareStatement(sql3)) {
                p1.setString(1, doubtId); p1.executeUpdate();
                p2.setString(1, answerId); p2.executeUpdate();
                p3.setString(1, doubtId); p3.executeUpdate();
                conn.commit(); return true;
            } catch (SQLException ex) { conn.rollback(); return false; }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean deleteDoubt(String doubtId) {
        String sql = "DELETE FROM academic_doubts WHERE id = ?::uuid";
        try (Connection conn = dataService.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, doubtId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    
}