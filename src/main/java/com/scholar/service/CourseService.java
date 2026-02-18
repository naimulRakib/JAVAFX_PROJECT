package com.scholar.service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseService {
    
    public CourseService() {
        // Empty constructor
    }

    // ==========================================
    // 📦 DATA MODELS
    // ==========================================
    public record Segment(int id, String name) {}
    public record Topic(int id, String title, String tag) {}
    public record UserProfile(String fullName, String username, String email) {}
 

    public record Resource(
        int id, 
        String title, 
        String link, 
        String type, 
        String difficulty, 
        String duration, 
        boolean isCompleted, 
        boolean isPublic,     
        int upvotes,          
        int downvotes,        
        String creatorName,
        String creatorId,
        int easyVotes,
        int mediumVotes,
        int hardVotes,
        boolean hasUserVoted,
        String tags ,
        String description 
        // ✅ Added tags to record
    ) {}

    // ==========================================
    // 📊 QUORA-STYLE STATISTICS MODELS (NEW)
    // ==========================================
    public record CompletionLog(String username, String difficulty, int timeMins, String note, String date) {}
    
    public record ResourceStats(
        int totalUpvotes, 
        int totalDownvotes, 
        int easyCount, 
        int mediumCount, 
        int hardCount, 
        List<CompletionLog> userLogs
    ) {}

    // ==========================================
    // 📚 COURSE & TOPIC MANAGEMENT
    // ==========================================

    public List<String> getAllCourseCodes() {
        List<String> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection(); 
        if (conn == null) return list;

        String sql = "SELECT code FROM courses ORDER BY code ASC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while(rs.next()) list.add(rs.getString("code"));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public int getCourseId(String code) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return -1;

        String sql = "SELECT id FROM courses WHERE code = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public boolean addCourse(String code, String title) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        if (getCourseId(code) != -1) return false; 

        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String insertCourse = "INSERT INTO courses (code, title, created_by) VALUES (?, ?, ?::uuid) RETURNING id";
        String insertSegment = "INSERT INTO segments (course_id, name) VALUES (?, ?)";

        try {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); 

            try (PreparedStatement pstmt = conn.prepareStatement(insertCourse)) {
                pstmt.setString(1, code);
                pstmt.setString(2, title);
                pstmt.setObject(3, AuthService.CURRENT_USER_ID);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int courseId = rs.getInt(1);
                    try (PreparedStatement segStmt = conn.prepareStatement(insertSegment)) {
                        String[] segments = { "CT-1", "CT-2", "CT-3", "CT-4", "Basic Building", "Term Final" };
                        for (String seg : segments) {
                            segStmt.setInt(1, courseId);
                            segStmt.setString(2, seg);
                            segStmt.addBatch();
                        }
                        segStmt.executeBatch();
                    }
                    conn.commit(); 
                    return true;
                }
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public List<Segment> getSegments(int courseId) {
        List<Segment> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT * FROM segments WHERE course_id = ? ORDER BY id ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) list.add(new Segment(rs.getInt("id"), rs.getString("name")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Topic> getTopics(int segmentId) {
        List<Topic> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        String sql = "SELECT * FROM topics WHERE segment_id = ? ORDER BY id ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) list.add(new Topic(rs.getInt("id"), rs.getString("title"), rs.getString("tag")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean addTopic(int segmentId, String title, String tag) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = "INSERT INTO topics (segment_id, title, tag, created_by) VALUES (?, ?, ?, ?::uuid)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setString(2, title);
            pstmt.setString(3, tag);
            pstmt.setObject(4, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ==========================================
    // 🌍 RESOURCES & PROGRESS FETCHING (FIXED)
    // ==========================================
    public List<Resource> getResources(int topicId) {
        List<Resource> list = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        // 🌟 SQL: Check if CURRENT USER voted and marked as done! 🌟
        String sql = """
            SELECT 
                r.*, 
                u.full_name as creator_name,
                (SELECT COUNT(*) FROM user_progress p WHERE p.resource_id = r.id AND p.difficulty_rating = 'Easy') as easy_cnt,
                (SELECT COUNT(*) FROM user_progress p WHERE p.resource_id = r.id AND p.difficulty_rating = 'Medium') as med_cnt,
                (SELECT COUNT(*) FROM user_progress p WHERE p.resource_id = r.id AND p.difficulty_rating = 'Hard') as hard_cnt,
                EXISTS(SELECT 1 FROM resource_votes v WHERE v.resource_id = r.id AND v.user_id = ?::uuid) as user_voted,
                EXISTS(SELECT 1 FROM user_progress p WHERE p.resource_id = r.id AND p.user_id = ?::uuid) as user_completed
            FROM resources r 
            LEFT JOIN users u ON r.created_by = u.id 
            WHERE r.topic_id = ? 
            ORDER BY r.upvotes DESC, r.id DESC
        """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // ইউজারের আইডি তিন জায়গায় পাস করা হচ্ছে
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            pstmt.setInt(3, topicId);
            
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) {
                list.add(new Resource(
                    rs.getInt("id"), rs.getString("title"), rs.getString("link"), rs.getString("type"),
                    rs.getString("difficulty"), rs.getString("duration"),
                    rs.getBoolean("user_completed"), // ✅ এটি "Done" বাটন ফিক্স করবে!
                    rs.getBoolean("is_public"),
                    rs.getInt("upvotes"), rs.getInt("downvotes"),
                    rs.getString("creator_name"), rs.getString("created_by"),
                    rs.getInt("easy_cnt"), rs.getInt("med_cnt"), rs.getInt("hard_cnt"),
                    rs.getBoolean("user_voted"), // ✅ এটি ভোটের পর বাটন Disable করবে!
                    rs.getString("tags"),
                    rs.getString("description")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }



 // 🌟 Updated Upload Method (Matches Supabase Schema perfectly) 🌟
  // 🌟 Updated Method: এখন এটি AI Summary গ্রহণ করবে এবং ডাটাবেসে সেভ করবে
// 🌟 Updated Method: channel_id সহ রিসোর্স সেভ করা
public boolean addDetailedResource(int topicId, String title, String link, String type, String desc, String tags, String difficulty, String duration, boolean isPublic, String aiSummary, int channelId) {
    String sql = "INSERT INTO resources (topic_id, title, link, type, description, tags, difficulty, duration, is_public, created_by, ai_summary, channel_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?, ?)";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setInt(1, topicId);
        pstmt.setString(2, title);
        pstmt.setString(3, link);
        pstmt.setString(4, type);
        pstmt.setString(5, desc);
        pstmt.setString(6, tags);
        pstmt.setString(7, difficulty);
        pstmt.setString(8, duration);
        pstmt.setBoolean(9, isPublic);
        pstmt.setObject(10, AuthService.CURRENT_USER_ID); //
        pstmt.setString(11, aiSummary != null ? aiSummary : "No summary generated."); // 👈 এটি নিশ্চিত করুন
        pstmt.setInt(12, channelId); // 👈 আপনার বর্তমান চ্যানেল

        return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
}





    public boolean updateProgress(int resId, boolean isDone, int mins) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = """
            INSERT INTO user_progress (user_id, resource_id, is_completed, time_spent_mins)
            VALUES (?::uuid, ?, ?, ?)
            ON CONFLICT (user_id, resource_id) 
            DO UPDATE SET is_completed = EXCLUDED.is_completed, time_spent_mins = EXCLUDED.time_spent_mins;
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setInt(2, resId);
            pstmt.setBoolean(3, isDone);
            pstmt.setInt(4, mins);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean deleteResource(int resId) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = "DELETE FROM resources WHERE id = ? AND created_by = ?::uuid";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, resId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ==========================================
    // 💬 SOCIAL FEATURES (VOTES & COMMENTS)
    // ==========================================

    public boolean voteResource(int resId, int type, String difficulty) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = """
            INSERT INTO resource_votes (user_id, resource_id, vote_type, difficulty_vote)
            VALUES (?::uuid, ?, ?, ?)
            ON CONFLICT (user_id, resource_id) 
            DO UPDATE SET vote_type = CASE WHEN ? != 0 THEN ? ELSE resource_votes.vote_type END,
                          difficulty_vote = CASE WHEN ? IS NOT NULL THEN ? ELSE resource_votes.difficulty_vote END;
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setInt(2, resId);
            pstmt.setInt(3, type);
            pstmt.setString(4, difficulty);
            pstmt.setInt(5, type);
            pstmt.setInt(6, type);
            pstmt.setString(7, difficulty);
            pstmt.setString(8, difficulty);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }



    //COMMENT SECTION ON DISCUSSION

    public record Comment(int id, String userId, String userName, String content, String time, int parentId) {}

    // ১. প্রশ্ন বা উত্তর যোগ করা
    public boolean addComment(int resId, String content, Integer parentId) {
        String sql = "INSERT INTO comments (resource_id, user_id, content, parent_id) VALUES (?, ?::uuid, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, resId);
            pstmt.setObject(2, AuthService.CURRENT_USER_ID);
            pstmt.setString(3, content);
            if (parentId == null || parentId == 0) pstmt.setNull(4, Types.INTEGER); // এটি একটি নতুন প্রশ্ন
            else pstmt.setInt(4, parentId); // এটি একটি উত্তর (Reply)
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    // ২



    public List<Comment> getComments(int resId) {
        List<Comment> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.user_id, c.content, c.created_at, c.parent_id, u.username, u.full_name 
            FROM comments c 
            LEFT JOIN users u ON c.user_id = u.id 
            WHERE c.resource_id = ? 
            ORDER BY c.created_at ASC
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, resId);
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) {
                String name = rs.getString("username") != null ? rs.getString("username") : rs.getString("full_name");
                if (name == null) name = "Student";
                
                String date = rs.getString("created_at") != null ? rs.getString("created_at").split(" ")[0] : "Recently";
                list.add(new Comment(rs.getInt("id"), rs.getString("user_id"), name, rs.getString("content"), date, rs.getInt("parent_id")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ==========================================
    // 👤 PROFILE METHODS
    // ==========================================

    public UserProfile getMyProfile() {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return new UserProfile("Student", "user", "email@example.com");

        String sql = "SELECT full_name, username, email FROM users WHERE id = ?::uuid";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            ResultSet rs = pstmt.executeQuery();
            if(rs.next()) return new UserProfile(rs.getString("full_name"), rs.getString("username"), rs.getString("email"));
        } catch (SQLException e) { e.printStackTrace(); }
        return new UserProfile("Student", "user", "email@example.com");
    }

    public boolean updateProfile(String fullName, String username) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String sql = "UPDATE users SET full_name = ?, username = ? WHERE id = ?::uuid";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fullName);
            pstmt.setString(2, username);
            pstmt.setObject(3, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ==========================================
    // 🚀 STEP 2: BACKEND LOGIC FOR QUORA SYSTEM (NEW)
    // ==========================================

   // ==========================================================
    // 🚀 SUBMIT VOTE & UPDATE RESOURCE TABLE
    // ==========================================================
    public boolean submitVote(int resourceId, int voteType) {
        // ১. ইউজারের ভোট সেভ করার SQL (যেন ডুপ্লিকেট ভোট না হয়)
        String insertVoteSql = """
            INSERT INTO resource_votes (user_id, resource_id, vote_type) 
            VALUES (?::uuid, ?, ?)
            ON CONFLICT (user_id, resource_id) DO UPDATE SET vote_type = EXCLUDED.vote_type;
        """;
        
        // ২. মেইন resources টেবিলের upvotes এবং downvotes কলাম আপডেট করার SQL
        String updateCountSql = """
            UPDATE resources 
            SET upvotes = (SELECT COUNT(*) FROM resource_votes WHERE resource_id = ? AND vote_type = 1),
                downvotes = (SELECT COUNT(*) FROM resource_votes WHERE resource_id = ? AND vote_type = -1)
            WHERE id = ?;
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // প্রথম কাজ: ইউজারের ভোট সেভ করা
            try (PreparedStatement pstmt = conn.prepareStatement(insertVoteSql)) {
                pstmt.setObject(1, AuthService.CURRENT_USER_ID);
                pstmt.setInt(2, resourceId);
                pstmt.setInt(3, voteType);
                pstmt.executeUpdate();
            }
            
            // দ্বিতীয় কাজ: resources টেবিলের কলাম আপডেট করা
            try (PreparedStatement pstmt = conn.prepareStatement(updateCountSql)) {
                pstmt.setInt(1, resourceId); // Upvote কাউন্টের জন্য
                pstmt.setInt(2, resourceId); // Downvote কাউন্টের জন্য
                pstmt.setInt(3, resourceId); // কোন রিসোর্স আপডেট হবে তার ID
                pstmt.executeUpdate();
            }
            
            return true;
        } catch (SQLException e) { 
            e.printStackTrace();
            return false; 
        }
    }




    // 2. Mark as Done (Saves Difficulty, Time, and User Notes)
    public boolean markResourceDone(int resourceId, String difficulty, int timeMins, String userNote) {
        String sql = """
            INSERT INTO user_progress (user_id, resource_id, difficulty_rating, time_spent_mins, user_note) 
            VALUES (?::uuid, ?, ?, ?, ?)
            ON CONFLICT (user_id, resource_id) DO UPDATE SET 
                difficulty_rating = EXCLUDED.difficulty_rating, 
                time_spent_mins = EXCLUDED.time_spent_mins, 
                user_note = EXCLUDED.user_note;
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setInt(2, resourceId);
            pstmt.setString(3, difficulty);
            pstmt.setInt(4, timeMins);
            pstmt.setString(5, userNote);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            System.err.println("Error saving progress: " + e.getMessage());
            return false; 
        }
    }



    

    // 3. Fetch Full Statistics for the "Statistics Tab/Popup"
    public ResourceStats getResourceStatistics(int resourceId) {
        int up = 0, down = 0, easy = 0, med = 0, hard = 0;
        List<CompletionLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // A. Get Votes
            String voteSql = "SELECT vote_type, COUNT(*) as cnt FROM resource_votes WHERE resource_id = ? GROUP BY vote_type";
            try (PreparedStatement pstmt = conn.prepareStatement(voteSql)) {
                pstmt.setInt(1, resourceId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    if (rs.getInt("vote_type") == 1) up = rs.getInt("cnt");
                    if (rs.getInt("vote_type") == -1) down = rs.getInt("cnt");
                }
            }

            // B. Get Difficulty Distribution & User Logs
            String progSql = """
                SELECT p.difficulty_rating, p.time_spent_mins, p.user_note, p.created_at, u.username, u.full_name 
                FROM user_progress p 
                JOIN users u ON p.user_id = u.id 
                WHERE p.resource_id = ?
                ORDER BY p.created_at DESC
            """;
            try (PreparedStatement pstmt = conn.prepareStatement(progSql)) {
                pstmt.setInt(1, resourceId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String diff = rs.getString("difficulty_rating");
                    if ("Easy".equals(diff)) easy++;
                    if ("Medium".equals(diff)) med++;
                    if ("Hard".equals(diff)) hard++;

                    String name = rs.getString("username") != null ? rs.getString("username") : rs.getString("full_name");
                    if (name == null) name = "Student";
                    
                    String date = rs.getString("created_at") != null ? rs.getString("created_at").split(" ")[0] : "Recently";

                    logs.add(new CompletionLog(name, diff, rs.getInt("time_spent_mins"), rs.getString("user_note"), date));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching stats: " + e.getMessage());
        }

        return new ResourceStats(up, down, easy, med, hard, logs);
    }




    // edit 
    // 🌟 Update/Edit Resource
    public boolean updateResource(int id, String title, String link, String type, String desc, String tags, String diff, String duration) {
        String sql = "UPDATE resources SET title=?, link=?, type=?, description=?, tags=?, difficulty=?, duration=? WHERE id=? AND created_by=?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title); pstmt.setString(2, link); pstmt.setString(3, type);
            pstmt.setString(4, desc); pstmt.setString(5, tags); pstmt.setString(6, diff);
            pstmt.setString(7, duration); pstmt.setInt(8, id); pstmt.setObject(9, AuthService.CURRENT_USER_ID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { 
            e.printStackTrace(); 
            return false; 
        }
    }

    // ==========================================================
    // 📝 FETCH EXISTING USER PROGRESS/NOTES
    // ==========================================================
    public record UserProgress(boolean isCompleted, String difficulty, int timeMins, String userNote) {}

    public UserProgress getUserProgress(int resourceId) {
        String sql = "SELECT difficulty_rating, time_spent_mins, user_note FROM user_progress WHERE user_id = ?::uuid AND resource_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setObject(1, AuthService.CURRENT_USER_ID);
            pstmt.setInt(2, resourceId);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // যদি আগে থেকে সেভ করা ডাটা থাকে, সেটি রিটার্ন করবে
                return new UserProgress(true, rs.getString("difficulty_rating"), rs.getInt("time_spent_mins"), rs.getString("user_note"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // যদি ডাটা না থাকে, ডিফল্ট ভ্যালু রিটার্ন করবে
        return new UserProgress(false, "Medium", 0, "");
    }


public String fetchSystematicResources(String userQuery) {
    StringBuilder context = new StringBuilder();
    // ইউজারের সার্চ টার্মকে প্রসেস করা (e.g., "cse 105 ct-1")
    String searchKeyword = "%" + userQuery.trim().replace(" ", "%") + "%";

    String sql = """
        SELECT r.title, r.link, r.type, r.difficulty, r.upvotes, r.ai_summary,
               c.code AS course_code, s.name AS segment_name, t.title AS topic_name,
               (SELECT STRING_AGG(content, ' | ') FROM comments WHERE resource_id = r.id) as feedback
        FROM resources r
        JOIN topics t ON r.topic_id = t.id
        JOIN segments s ON t.segment_id = s.id
        JOIN courses c ON s.course_id = c.id
        WHERE (c.code ILIKE ? OR s.name ILIKE ? OR t.title ILIKE ? OR r.tags ILIKE ?)
        AND r.channel_id = ?
        ORDER BY r.upvotes DESC, r.difficulty ASC; 
    """;
    // র‍্যাংকিং লজিক: প্রথমে সবচেয়ে বেশি Upvotes, তারপর কম Difficulty।

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        for (int i = 1; i <= 4; i++) pstmt.setString(i, searchKeyword);
        pstmt.setInt(5, AuthService.CURRENT_CHANNEL_ID);

        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            context.append("[RESOURCE FOUND]\n")
                   .append("Category: ").append(rs.getString("course_code")).append(" > ")
                   .append(rs.getString("segment_name")).append(" > ").append(rs.getString("topic_name")).append("\n")
                   .append("Title: ").append(rs.getString("title")).append("\n")
                   .append("Link: ").append(rs.getString("link")).append("\n")
                   .append("AI Insight: ").append(rs.getString("ai_summary")).append("\n")
                   .append("Ranking Factor: Upvotes(").append(rs.getInt("upvotes")).append("), Difficulty(").append(rs.getString("difficulty")).append(")\n")
                   .append("Student Discussions: ").append(rs.getString("feedback") != null ? rs.getString("feedback") : "No specific feedback.").append("\n\n");
        }
    } catch (SQLException e) { e.printStackTrace(); }
    return context.toString();
}



    // 🧠 এআই-এর জন্য রিসোর্স এবং ডিসকাশন ডাটা একসাথে আনা
public String fetchComprehensiveContext(String userQuery) {
    StringBuilder context = new StringBuilder();
    String searchKeyword = "%" + userQuery.trim().replace(" ", "%") + "%";

    // SQL: রিসোর্স ডাটা + ওই রিসোর্সের সব কমেন্ট (Discussion) এগ্রিগেট করা
    String sql = """
        SELECT r.id, r.title, r.link, r.ai_summary, r.description, r.difficulty, r.upvotes,
               c.code, s.name as segment, t.title as topic,
               (SELECT STRING_AGG('User Says: ' || content, ' | ') FROM comments WHERE resource_id = r.id) as discussion
        FROM resources r
        JOIN topics t ON r.topic_id = t.id
        JOIN segments s ON t.segment_id = s.id
        JOIN courses c ON s.course_id = c.id
        WHERE c.code ILIKE ? OR s.name ILIKE ? OR t.title ILIKE ? OR r.title ILIKE ? OR r.tags ILIKE ?
        ORDER BY r.upvotes DESC LIMIT 5
    """;

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        for (int i = 1; i <= 5; i++) pstmt.setString(i, searchKeyword);
        ResultSet rs = pstmt.executeQuery();
        
        while (rs.next()) {
            context.append("--- RESOURCE ---\n")
                   .append("Title: ").append(rs.getString("title")).append("\n")
                   .append("Course: ").append(rs.getString("code")).append(" (").append(rs.getString("segment")).append(")\n")
                   .append("AI Summary: ").append(rs.getString("ai_summary")).append("\n")
                   .append("Community Feedback: ").append(rs.getString("discussion") != null ? rs.getString("discussion") : "No discussion yet.").append("\n")
                   .append("Link: ").append(rs.getString("link")).append("\n\n");
        }
    } catch (SQLException e) { e.printStackTrace(); }
    return context.toString();
}
    



}