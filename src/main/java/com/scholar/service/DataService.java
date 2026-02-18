package com.scholar.service;

import com.scholar.model.StudyTask;
import io.github.cdimascio.dotenv.Dotenv;
import com.scholar.service.AuthService; // 

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataService {

    private final String DB_URL;
    private final String DB_USER;
    private final String DB_PASSWORD;

    public DataService() {
        Dotenv dotenv = Dotenv.load();
        this.DB_URL = dotenv.get("DB_URL");
        this.DB_USER = dotenv.get("DB_USER");
        this.DB_PASSWORD = dotenv.get("DB_PASSWORD");
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // =================================================================
    //  SECTION 1: STUDY TASKS (ROUTINE, NOTICE & CALENDAR)
    // =================================================================

    // ==========================================================
    // 💾 SAVE TASKS TO DATABASE (Updated for 13 Parameters)
    // ==========================================================
    public boolean saveTasks(List<StudyTask> tasks) {
        if (AuthService.CURRENT_USER_ID == null || tasks == null || tasks.isEmpty()) return false;
        
        // 🌟 ফিক্স: status এবং importance কলাম যোগ করা হয়েছে
        String sql = "INSERT INTO study_tasks (title, task_date, start_time, duration_minutes, room_no, type, description, creator_role, user_id, channel_id, ct_course, ct_syllabus, status, importance) VALUES (?, ?::date, ?, ?, ?, ?, ?, ?, ?::uuid, ?, ?, ?, ?, ?)";
        
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (StudyTask task : tasks) {
                pstmt.setString(1, task.title());
                pstmt.setString(2, task.date());
                pstmt.setString(3, task.startTime());
                
                if (task.durationMinutes() != null) pstmt.setInt(4, task.durationMinutes());
                else pstmt.setNull(4, java.sql.Types.INTEGER);
                
                pstmt.setString(5, task.roomNo() != null && !task.roomNo().isEmpty() ? task.roomNo() : null);
                pstmt.setString(6, task.type());
                pstmt.setString(7, task.tags());
                pstmt.setString(8, task.creatorRole());
                pstmt.setString(9, String.valueOf(AuthService.CURRENT_USER_ID));
                
                if (AuthService.CURRENT_CHANNEL_ID != -1) pstmt.setInt(10, AuthService.CURRENT_CHANNEL_ID);
                else pstmt.setNull(10, java.sql.Types.INTEGER);
                
                pstmt.setString(11, task.ctCourse());
                pstmt.setString(12, task.ctSyllabus());
                pstmt.setString(13, task.status() != null ? task.status() : "PENDING"); // 🌟 Status
                pstmt.setString(14, task.importance() != null ? task.importance() : "Medium"); // 🌟 Importance
                
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    

    // 2. LOAD ALL TASKS (Unified Timeline Magic)
    public List<StudyTask> loadAllTasks() {
        if (AuthService.CURRENT_USER_ID == null) return new ArrayList<>();

        List<StudyTask> tasks = new ArrayList<>();
        
        // 🌟 THE MAGIC QUERY: Fetch Personal Tasks OR Channel's Global Admin Tasks
        String sql = "SELECT * FROM study_tasks WHERE user_id = ? OR (channel_id = ? AND type IN ('ROUTINE', 'NOTICE'))";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setInt(2, AuthService.CURRENT_CHANNEL_ID);
            
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // Adapting to the new StudyTask record format
             tasks.add(new StudyTask(
                    rs.getString("id"), rs.getString("title"),
                    rs.getDate("task_date") != null ? rs.getDate("task_date").toString() : null,
                    rs.getString("start_time"), rs.getInt("duration_minutes"),
                    rs.getString("room_no"), rs.getString("type"),
                    rs.getString("description"), rs.getString("creator_role"),
                    rs.getString("ct_course"), rs.getString("ct_syllabus"),
                    rs.getString("status"),      // 🌟 NEW
                    rs.getString("importance")   // 🌟 NEW
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    // 3. DELETE A SPECIFIC TASK (Security Protected)
    public void deleteTask(String taskId, String type, String creatorRole) {
        if (AuthService.CURRENT_USER_ID == null) return;

        // 🔒 Security: Only Admin can delete ROUTINE or NOTICE
        if (("ROUTINE".equals(type) || "NOTICE".equals(type)) && !"admin".equals(AuthService.CURRENT_USER_ROLE)) {
            System.err.println("❌ Security Alert: Student tried to delete an Admin broadcast.");
            return;
        }

        String sql = "DELETE FROM study_tasks WHERE id = ?::uuid";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, taskId);
            pstmt.executeUpdate();
            System.out.println("🗑 Deleted task ID: " + taskId);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 4. BULK DELETE BY DATE (Kept for your AI Agent)
    public void deleteTasksByDate(String date, String type) {
        if (AuthService.CURRENT_USER_ID == null) return;

        // Ensure agent doesn't wipe out Varsity Routines by mistake!
        if ("ROUTINE".equals(type) || "NOTICE".equals(type)) return;

        String sql = "DELETE FROM study_tasks WHERE task_date = ? AND type = ? AND user_id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, type); 
            pstmt.setObject(3, AuthService.CURRENT_USER_ID);
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 5. CLEAR OLD "REGULAR" ROUTINE
    public void clearRegularTasks() {
        if (AuthService.CURRENT_USER_ID == null) return;

        String sql = "DELETE FROM study_tasks WHERE type = 'Regular' AND user_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =================================================================
    //  SECTION 2: RESOURCES (NOTES) - Unchanged
    // =================================================================
    public record Resource(int id, String title, String link, String type, String content) {}

    public boolean addResource(String title, String link, String type, String content) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        String sql = "INSERT INTO resources (title, link, type, content, user_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title); pstmt.setString(2, link); pstmt.setString(3, type); pstmt.setString(4, content); pstmt.setObject(5, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<Resource> getAllResources() {
        if (AuthService.CURRENT_USER_ID == null) return new ArrayList<>();
        List<Resource> list = new ArrayList<>();
        String sql = "SELECT * FROM resources WHERE user_id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) { list.add(new Resource(rs.getInt("id"), rs.getString("title"), rs.getString("link"), rs.getString("type"), rs.getString("content"))); }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean deleteResource(int id) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        String sql = "DELETE FROM resources WHERE id = ? AND user_id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id); pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }


    // =================================================================
    //  UPDATE TASK DETAILS (Syllabus, Room, Title)
    // =================================================================
    public boolean updateTaskDetails(String taskId, String newTitle, String newRoom, String newDescription) {
        if (AuthService.CURRENT_USER_ID == null) return false;

        // Title, Room এবং Description (Syllabus) আপডেট করার SQL
        String sql = "UPDATE study_tasks SET title = ?, room_no = ?, description = ? WHERE id = ?::uuid";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, newTitle);
            
            if (newRoom == null || newRoom.trim().isEmpty()) pstmt.setNull(2, Types.VARCHAR);
            else pstmt.setString(2, newRoom.trim());
            
            pstmt.setString(3, newDescription);
            pstmt.setString(4, taskId);
            
            return pstmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            System.err.println("❌ Error updating task: " + e.getMessage());
            return false;
        }
    }


 // =================================================================
    //  SAVE CT & REGULAR & PERSONAL DETAILS TO SUPABASE
    // =================================================================
    public boolean updateTaskDetails(String taskId, String title, String room, String desc, String ctCourse, String ctSyllabus, String importance) {
        String sql = "UPDATE study_tasks SET title = ?, room_no = ?, description = ?, ct_course = ?, ct_syllabus = ?, importance = ? WHERE id = ?::uuid";
        
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, room != null && !room.isEmpty() ? room : null);
            pstmt.setString(3, desc);
            pstmt.setString(4, ctCourse);
            pstmt.setString(5, ctSyllabus);
            pstmt.setString(6, importance); // 🌟 NEW: Importance ডাটাবেসে সেভ হবে
            pstmt.setString(7, taskId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    


    // ==========================================================
    // 🔄 AUTO-MOVE TO BACKLOG (Past Date & Pending)
    // ==========================================================
    public void autoMoveToBacklog() {
        if (AuthService.CURRENT_USER_ID == null) return;
        // যদি আজকের আগের ডেট হয় এবং পেন্ডিং থাকে, তবে সেগুলোকে ব্যাকলগ বানিয়ে দাও
        String sql = "UPDATE study_tasks SET status = 'BACKLOG' WHERE type = 'PERSONAL' AND status = 'PENDING' AND task_date < CURRENT_DATE AND user_id = ?::uuid";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
           // 🌟 ফিক্স: CURRENT_USER_ID যদি Integer-ও হয়, সেটিকে String-এ কনভার্ট করে নেবে
            pstmt.setString(1, String.valueOf(AuthService.CURRENT_USER_ID));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Backlog Update Error: " + e.getMessage());
        }
    }

    // ==========================================================
    // ✅ UPDATE STATUS (Complete or Reschedule)
    // ==========================================================
    public boolean updateTaskStatus(String taskId, String newStatus, String newDate, String newTime) {
        String sql = "UPDATE study_tasks SET status = ?, task_date = ?::date, start_time = ? WHERE id = ?::uuid";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setString(2, newDate); 
            pstmt.setString(3, newTime);
            pstmt.setString(4, taskId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }



}