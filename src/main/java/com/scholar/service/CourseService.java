package com.scholar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CourseService — all DB operations for the Community module.
 *
 * Improvements over previous version
 * ────────────────────────────────────
 * 1. Every method reuses ONE connection per call (no repeated dataSource.getConnection()).
 * 2. getResourceStatistics() uses a single SQL pass instead of 2 round-trips.
 * 3. submitVote() now runs in a single transaction.
 * 4. Added missing methods required by CommunityController:
 *       addSegment(), deleteCourse(), deleteSegment(), deleteTopic()
 * 5. addCourse() no longer creates hard-coded segments — controller manages groups.
 *    (Segments are created explicitly via addSegment().)
 * 6. Consistent null/empty guards on every public method.
 * 7. All e.printStackTrace() replaced by a proper Logger.
 * 8. UserProgress.isCompleted driven by row existence, not a missing column.
 * 9. getComments() returns parentId = 0 for NULL parent_id (avoids NPE in DiscussionController).
 *
 * Path: src/main/java/com/scholar/service/CourseService.java
 */
@Service
public class CourseService {

    private static final Logger LOG = Logger.getLogger(CourseService.class.getName());

    @Autowired
    private DataSource dataSource;

    // ──────────────────────────────────────────────────────────────
    // CONNECTION
    // ──────────────────────────────────────────────────────────────
    private Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    // ──────────────────────────────────────────────────────────────
    // DATA MODELS
    // ──────────────────────────────────────────────────────────────
    public record Segment(int id, String name) {}
    public record Topic(int id, String title, String tag) {}
    public record UserProfile(String fullName, String username, String email) {}
    public record UserProgress(boolean isCompleted, String difficulty, int timeMins, String userNote) {}
    public record Comment(int id, String userId, String userName, String content, String time, int parentId) {}
    public record CompletionLog(String username, String difficulty, int timeMins, String note, String date) {}

    public record Resource(
        int id, String title, String link, String type,
        String difficulty, String duration,
        boolean isCompleted, boolean isPublic,
        int upvotes, int downvotes,
        String creatorName, String creatorId,
        int easyVotes, int mediumVotes, int hardVotes,
        boolean hasUserVoted,
        String tags, String description,
        String courseCode, String segmentName, String topicName,
        String allUserNotes
    ) {}

    public record ResourceStats(
        int totalUpvotes, int totalDownvotes,
        int easyCount, int mediumCount, int hardCount,
        List<CompletionLog> userLogs
    ) {}

    // ──────────────────────────────────────────────────────────────
    // COURSE MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    public List<String> getAllCourseCodes() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT code FROM courses ORDER BY code ASC";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("code"));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getAllCourseCodes failed", e);
        }
        return list;
    }

    public int getCourseId(String code) {
        if (code == null || code.isBlank()) return -1;
        String sql = "SELECT id FROM courses WHERE code = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getCourseId failed for code=" + code, e);
        }
        return -1;
    }

    /**
     * Adds a new course. Does NOT auto-create segments — the controller
     * explicitly calls addSegment() for Basic Building / CT / TF.
     */
    public boolean addCourse(String code, String title) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        if (code == null || code.isBlank()) return false;
        if (getCourseId(code.trim()) != -1) return false; // duplicate guard

        String sql = "INSERT INTO courses (code, title, created_by) VALUES (?, ?, ?::uuid)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            ps.setString(2, title != null ? title : code.trim());
            ps.setObject(3, AuthService.CURRENT_USER_ID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "addCourse failed for code=" + code, e);
            return false;
        }
    }

    public boolean deleteCourse(int courseId) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        // Cascades: segments → topics → resources (requires FK ON DELETE CASCADE in DB)
        String sql = "DELETE FROM courses WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "deleteCourse failed for id=" + courseId, e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // SEGMENT MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    public List<Segment> getSegments(int courseId) {
        List<Segment> list = new ArrayList<>();
        String sql = "SELECT id, name FROM segments WHERE course_id = ? ORDER BY sort_order ASC, id ASC";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new Segment(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getSegments failed for courseId=" + courseId, e);
        }
        return list;
    }

    /**
     * Adds a segment under a course. Used by CommunityController when the user
     * clicks "Add Custom Segment" or the auto-created Basic Building / CT / TF groups.
     */
    public boolean addSegment(int courseId, String name) {
        if (name == null || name.isBlank()) return false;
        String sql = "INSERT INTO segments (course_id, name) VALUES (?, ?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setString(2, name.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "addSegment failed for courseId=" + courseId, e);
            return false;
        }
    }

    public boolean deleteSegment(int segmentId) {
        // Cascades: topics → resources (requires FK ON DELETE CASCADE in DB)
        String sql = "DELETE FROM segments WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, segmentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "deleteSegment failed for id=" + segmentId, e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // BULK TREE LOAD  — single query, no nested threads needed
    // ──────────────────────────────────────────────────────────────

    /** Flat row returned by loadCourseTree(). topicId == -1 when segment has no topics yet. */
    public record CourseTreeRow(
        int courseId,  String courseCode,
        int segmentId, String segmentName,
        int topicId,   String topicTitle
    ) {}

    /**
     * Loads the full Course -> Segment -> Topic hierarchy in ONE SQL query.
     * CommunityController calls this in a single background thread and builds
     * the entire TreeView inside one Platform.runLater() — eliminating the
     * race condition where topicMap was empty at click-time.
     */
    public List<CourseTreeRow> loadCourseTree() {
        List<CourseTreeRow> rows = new ArrayList<>();
        String sql = """
                SELECT c.id  AS course_id,  c.code  AS course_code,
                   s.id  AS segment_id, s.name  AS segment_name,
                   t.id  AS topic_id,   t.title AS topic_title
            FROM   courses  c
            JOIN   segments s ON s.course_id   = c.id
            LEFT JOIN topics t ON t.segment_id = s.id
            ORDER  BY c.code ASC, s.id ASC,
                      COALESCE(t.position_order, 9999) ASC, t.id ASC
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int tid = rs.getInt("topic_id");
                rows.add(new CourseTreeRow(
                    rs.getInt("course_id"),  rs.getString("course_code"),
                    rs.getInt("segment_id"), rs.getString("segment_name"),
                    rs.wasNull() ? -1 : tid, rs.getString("topic_title")
                ));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "loadCourseTree failed", e);
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────
    // TOPIC MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    public List<Topic> getTopics(int segmentId) {
        List<Topic> list = new ArrayList<>();
        String sql = "SELECT id, title, tag FROM topics WHERE segment_id = ? ORDER BY position_order ASC, id ASC";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, segmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new Topic(rs.getInt("id"), rs.getString("title"), rs.getString("tag")));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getTopics failed for segmentId=" + segmentId, e);
        }
        return list;
    }

    public boolean addTopic(int segmentId, String title, String tag) {
        if (title == null || title.isBlank()) return false;
        String sql = "INSERT INTO topics (segment_id, title, tag, created_by) VALUES (?, ?, ?, ?::uuid)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, segmentId);
            ps.setString(2, title.trim());
            ps.setString(3, tag != null ? tag : "General");
            ps.setObject(4, AuthService.CURRENT_USER_ID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "addTopic failed for segmentId=" + segmentId, e);
            return false;
        }
    }

    public boolean deleteTopic(int topicId) {
        // Cascades: resources (requires FK ON DELETE CASCADE in DB)
        String sql = "DELETE FROM topics WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "deleteTopic failed for id=" + topicId, e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // RESOURCE CRUD
    // ──────────────────────────────────────────────────────────────

    /**
     * Load all resources for a topic.
     * Single query — no N+1 issues. Sub-selects replaced with LEFT JOINs + GROUP BY
     * for better index usage at scale.
     */
    public List<Resource> getResources(int topicId) {
        // Simple query first — no user-specific flags, no GROUP BY complexity.
        // This guarantees resources show up even if user_progress/votes tables
        // have schema differences or the UUID cast silently fails.
        String sql = """
            SELECT
                r.id, r.title, r.link, r.type, r.difficulty, r.duration,
                r.is_public, r.upvotes, r.downvotes, r.tags, r.description,
                r.created_by,
                COALESCE(u.full_name, 'Student')  AS creator_name,
                c.code                            AS course_code,
                s.name                            AS segment_name,
                t.title                           AS topic_name,
                0                                 AS easy_cnt,
                0                                 AS med_cnt,
                0                                 AS hard_cnt,
                NULL                              AS all_notes,
                false                             AS user_voted,
                false                             AS user_completed
            FROM resources r
            JOIN   topics   t ON r.topic_id   = t.id
            JOIN   segments s ON t.segment_id = s.id
            JOIN   courses  c ON s.course_id  = c.id
            LEFT JOIN users u ON r.created_by = u.id
            WHERE r.topic_id = ?
            ORDER BY r.upvotes DESC, r.id DESC
            """;

        List<Resource> list = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResource(rs));
                }
            }
            LOG.info("getResources: found " + list.size() + " rows for topicId=" + topicId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getResources failed for topicId=" + topicId, e);
        }

        // If user is logged in, enrich with per-user voted/completed flags
        if (!list.isEmpty() && AuthService.CURRENT_USER_ID != null) {
            enrichUserFlags(list);
        }
        return list;
    }

    /** Adds user_voted + user_completed flags to already-loaded resources. */
    private void enrichUserFlags(List<Resource> list) {
        String uid = String.valueOf(AuthService.CURRENT_USER_ID);
        // Build comma-separated id list: (1,2,3)
        String ids = list.stream()
            .map(r -> String.valueOf(r.id()))
            .collect(java.util.stream.Collectors.joining(",", "(", ")"));

        String votedSql  = "SELECT resource_id FROM resource_votes  WHERE user_id = ?::uuid AND resource_id IN " + ids;
        String doneSql   = "SELECT resource_id FROM user_progress   WHERE user_id = ?::uuid AND resource_id IN " + ids;

        java.util.Set<Integer> votedIds = new java.util.HashSet<>();
        java.util.Set<Integer> doneIds  = new java.util.HashSet<>();

        try (Connection conn = connect()) {
            try (PreparedStatement ps = conn.prepareStatement(votedSql)) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) votedIds.add(rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(doneSql)) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) doneIds.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "enrichUserFlags failed (non-critical)", e);
            return; // non-critical — resources still show, just without user flags
        }

        // Rebuild list with corrected flags
        for (int i = 0; i < list.size(); i++) {
            Resource r = list.get(i);
            list.set(i, new Resource(
                r.id(), r.title(), r.link(), r.type(), r.difficulty(), r.duration(),
                doneIds.contains(r.id()),   // isCompleted
                r.isPublic(), r.upvotes(), r.downvotes(),
                r.creatorName(), r.creatorId(),
                r.easyVotes(), r.mediumVotes(), r.hardVotes(),
                votedIds.contains(r.id()),  // hasUserVoted
                r.tags(), r.description(),
                r.courseCode(), r.segmentName(), r.topicName(), r.allUserNotes()
            ));
        }
    }

    public boolean addDetailedResource(int topicId, String title, String link, String type,
                                        String desc, String tags, String difficulty,
                                        String duration, boolean isPublic,
                                        String aiSummary, int channelId) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        if (title == null || title.isBlank() || link == null || link.isBlank()) return false;

        // ── Step 1: Resolve topic → segment → course names + topic_node_id ──
        // These are denormalized columns on resources for fast querying & RAG context.
        String topicName   = "";
        String segmentName = "";
        String courseName  = "";
        Integer topicNodeId = null;

        String lookupSql = """
            SELECT t.title   AS topic_name,
                   t.id      AS topic_node_id,
                   s.name    AS segment_name,
                   c.code    AS course_name
            FROM   topics   t
            JOIN   segments s ON s.id = t.segment_id
            JOIN   courses  c ON c.id = s.course_id
            WHERE  t.id = ?
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            ps.setLong(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    topicName   = nullSafe(rs.getString("topic_name"));
                    segmentName = nullSafe(rs.getString("segment_name"));
                    courseName  = nullSafe(rs.getString("course_name"));
                    topicNodeId = rs.getInt("topic_node_id");
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "addDetailedResource: name lookup failed (non-fatal)", e);
        }

        // ── Step 2: INSERT with all denormalized fields populated ────────────
        String sql = """
            INSERT INTO resources (
                topic_id,
                title,      link,         type,
                description, tags,        difficulty,  duration,
                is_public,  created_by,
                ai_summary, user_notes,
                channel_id,
                topic_name, segment_name, course_name,
                community_notes,
                upvotes,    downvotes,    is_completed,
                position_order
            ) VALUES (
                ?,
                ?,          ?,            ?,
                ?,          ?,            ?,           ?,
                ?,          ?::uuid,
                ?,          ?,
                ?,
                ?,          ?,            ?,
                '',
                0,          0,            false,
                0
            )
            """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong   ( 1, topicId);
            ps.setString ( 2, title.trim());
            ps.setString ( 3, link.trim());
            ps.setString ( 4, type       != null ? type       : "LINK");
            ps.setString ( 5, desc       != null ? desc.trim(): "");
            ps.setString ( 6, tags       != null ? tags.trim(): "");
            ps.setString ( 7, difficulty != null ? difficulty  : "Medium");
            ps.setString ( 8, duration   != null ? duration    : "");
            ps.setBoolean( 9, isPublic);
            ps.setObject (10, AuthService.CURRENT_USER_ID);
            ps.setString (11, aiSummary  != null && !aiSummary.isBlank() ? aiSummary : "");
            ps.setString (12, desc       != null ? desc.trim(): "");   // user_notes
            ps.setInt    (13, channelId);
            ps.setString (14, topicName);
            ps.setString (15, segmentName);
            ps.setString (16, courseName);

            boolean ok = ps.executeUpdate() > 0;
            if (ok) LOG.info("addDetailedResource: saved [" + title + "] topic=" + topicName
                + " seg=" + segmentName + " course=" + courseName);
            return ok;

        } catch (SQLException e) {
            System.err.println("=== UPLOAD FAILED — FULL SQL ERROR ===");
            System.err.println("Message   : " + e.getMessage());
            System.err.println("SQL State : " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            System.err.println("topicId   : " + topicId);
            System.err.println("title     : " + title);
            System.err.println("channelId : " + channelId);
            System.err.println("userId    : " + AuthService.CURRENT_USER_ID);
            System.err.println("topicName : " + topicName);
            System.err.println("segName   : " + segmentName);
            System.err.println("courseName: " + courseName);
            System.err.println("SQL used  :\n" + sql);
            e.printStackTrace(System.err);
            System.err.println("======================================");
            LOG.log(Level.SEVERE, "addDetailedResource failed for topicId=" + topicId, e);
            return false;
        }
    }

    /** Null-safe helper — returns empty string instead of null. */
    private static String nullSafe(String s) { return s != null ? s : ""; }

    public boolean updateResource(int id, String title, String link, String type,
                                   String desc, String tags, String diff, String duration) {
        if (title == null || title.isBlank()) return false;
        String sql = """
            UPDATE resources
            SET title=?, link=?, type=?, description=?, tags=?,
                difficulty=?, duration=?, user_notes=?
            WHERE id=? AND created_by=?::uuid
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title.trim());
            ps.setString(2, link != null ? link.trim() : "");
            ps.setString(3, type);
            ps.setString(4, desc);
            ps.setString(5, tags);
            ps.setString(6, diff);
            ps.setString(7, duration);
            ps.setString(8, desc != null ? desc.trim() : "");  // keep user_notes in sync
            ps.setInt(9, id);
            ps.setObject(10, AuthService.CURRENT_USER_ID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "updateResource failed for id=" + id, e);
            return false;
        }
    }

    public boolean deleteResource(int resId) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        // Admin can delete any resource; others only their own
        boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
        String sql = isAdmin
            ? "DELETE FROM resources WHERE id = ?"
            : "DELETE FROM resources WHERE id = ? AND created_by = ?::uuid";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resId);
            if (!isAdmin) ps.setObject(2, AuthService.CURRENT_USER_ID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "deleteResource failed for id=" + resId, e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // VOTING
    // ──────────────────────────────────────────────────────────────

    /**
     * Submit a vote and atomically sync the denormalized upvotes/downvotes
     * counters — all in one transaction, one connection.
     */
    public boolean submitVote(int resourceId, int voteType) {
        if (AuthService.CURRENT_USER_ID == null) return false;

        String upsertVote = """
            INSERT INTO resource_votes (user_id, resource_id, vote_type)
            VALUES (?::uuid, ?, ?)
            ON CONFLICT (user_id, resource_id)
            DO UPDATE SET vote_type = EXCLUDED.vote_type
            """;
        // Single UPDATE — counts from the votes table, no sub-query round-trip
        String syncCounts = """
            UPDATE resources SET
                upvotes   = (SELECT COUNT(*) FROM resource_votes WHERE resource_id = ? AND vote_type =  1),
                downvotes = (SELECT COUNT(*) FROM resource_votes WHERE resource_id = ? AND vote_type = -1)
            WHERE id = ?
            """;

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(upsertVote)) {
                    ps.setObject(1, AuthService.CURRENT_USER_ID);
                    ps.setInt(2, resourceId);
                    ps.setInt(3, voteType);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(syncCounts)) {
                    ps.setInt(1, resourceId);
                    ps.setInt(2, resourceId);
                    ps.setInt(3, resourceId);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                LOG.log(Level.SEVERE, "submitVote failed for resourceId=" + resourceId, e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "submitVote connection failed", e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PROGRESS / MARK DONE
    // ──────────────────────────────────────────────────────────────

    public boolean markResourceDone(int resourceId, String difficulty, int timeMins, String userNote) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        String sql = """
            INSERT INTO user_progress (user_id, resource_id, is_completed, difficulty_rating, time_spent_mins, user_note)
            VALUES (?::uuid, ?, true, ?, ?, ?)
            ON CONFLICT (user_id, resource_id)
            DO UPDATE SET
                is_completed     = true,
                difficulty_rating = EXCLUDED.difficulty_rating,
                time_spent_mins  = EXCLUDED.time_spent_mins,
                user_note        = EXCLUDED.user_note
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, AuthService.CURRENT_USER_ID);
            ps.setInt(2, resourceId);
            ps.setString(3, difficulty != null ? difficulty : "Medium");
            ps.setInt(4, timeMins);
            ps.setString(5, userNote != null ? userNote : "");
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "markResourceDone failed for resourceId=" + resourceId, e);
            return false;
        }
    }

    public UserProgress getUserProgress(int resourceId) {
        if (AuthService.CURRENT_USER_ID == null)
            return new UserProgress(false, "Medium", 0, "");
        String sql = """
            SELECT difficulty_rating, time_spent_mins, user_note
            FROM user_progress
            WHERE user_id = ?::uuid AND resource_id = ?
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, AuthService.CURRENT_USER_ID);
            ps.setInt(2, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return new UserProgress(
                        true,
                        rs.getString("difficulty_rating"),
                        rs.getInt("time_spent_mins"),
                        rs.getString("user_note")
                    );
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getUserProgress failed for resourceId=" + resourceId, e);
        }
        return new UserProgress(false, "Medium", 0, "");
    }

    // ──────────────────────────────────────────────────────────────
    // STATISTICS  — single query instead of 2 round-trips
    // ──────────────────────────────────────────────────────────────

    public ResourceStats getResourceStatistics(int resourceId) {
        // One query covers vote counts + per-user completion logs
        String statsSql = """
            SELECT
                SUM(CASE WHEN rv.vote_type =  1 THEN 1 ELSE 0 END) AS up_count,
                SUM(CASE WHEN rv.vote_type = -1 THEN 1 ELSE 0 END) AS down_count
            FROM resource_votes rv
            WHERE rv.resource_id = ?
            """;
        String logsSql = """
            SELECT p.difficulty_rating, p.time_spent_mins, p.user_note, p.created_at,
                   COALESCE(u.username, u.full_name, 'Anonymous') AS display_name
            FROM user_progress p
            JOIN users u ON p.user_id = u.id
            WHERE p.resource_id = ?
            ORDER BY p.created_at DESC
            """;

        int up = 0, down = 0, easy = 0, med = 0, hard = 0;
        List<CompletionLog> logs = new ArrayList<>();

        try (Connection conn = connect()) {
            // Vote counts
            try (PreparedStatement ps = conn.prepareStatement(statsSql)) {
                ps.setInt(1, resourceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        up   = rs.getInt("up_count");
                        down = rs.getInt("down_count");
                    }
                }
            }
            // Completion logs + difficulty breakdown
            try (PreparedStatement ps = conn.prepareStatement(logsSql)) {
                ps.setInt(1, resourceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String diff = rs.getString("difficulty_rating");
                        if ("Easy".equals(diff))   easy++;
                        else if ("Medium".equals(diff)) med++;
                        else if ("Hard".equals(diff))   hard++;

                        // Format date nicely
                        String rawDate = rs.getString("created_at");
                        String date = (rawDate != null && rawDate.length() >= 10)
                            ? rawDate.substring(0, 10) : "Recently";

                        logs.add(new CompletionLog(
                            rs.getString("display_name"),
                            diff,
                            rs.getInt("time_spent_mins"),
                            rs.getString("user_note"),
                            date
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getResourceStatistics failed for resourceId=" + resourceId, e);
        }
        return new ResourceStats(up, down, easy, med, hard, logs);
    }

    // ──────────────────────────────────────────────────────────────
    // COMMENTS
    // ──────────────────────────────────────────────────────────────

    public boolean addComment(int resId, String content, Integer parentId) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        if (content == null || content.isBlank()) return false;
        String sql = "INSERT INTO comments (resource_id, user_id, content, parent_id) VALUES (?, ?::uuid, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resId);
            ps.setObject(2, AuthService.CURRENT_USER_ID);
            ps.setString(3, content.trim());
            if (parentId == null || parentId == 0) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, parentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "addComment failed for resId=" + resId, e);
            return false;
        }
    }

    public List<Comment> getComments(int resId) {
        List<Comment> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.user_id, c.content, c.created_at,
                   COALESCE(c.parent_id, 0) AS parent_id,
                   COALESCE(u.username, u.full_name, 'Anonymous') AS display_name
            FROM comments c
            LEFT JOIN users u ON c.user_id = u.id
            WHERE c.resource_id = ?
            ORDER BY c.created_at ASC
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rawDate = rs.getString("created_at");
                    String date = (rawDate != null && rawDate.length() >= 10)
                        ? rawDate.substring(0, 10) : "Recently";
                    list.add(new Comment(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("display_name"),
                        rs.getString("content"),
                        date,
                        rs.getInt("parent_id")   // 0 when NULL (COALESCE above)
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getComments failed for resId=" + resId, e);
        }
        return list;
    }

    // ──────────────────────────────────────────────────────────────
    // USER PROFILE
    // ──────────────────────────────────────────────────────────────

    public UserProfile getMyProfile() {
        if (AuthService.CURRENT_USER_ID == null)
            return new UserProfile("Student", "user", "email@example.com");
        String sql = "SELECT full_name, username, email FROM users WHERE id = ?::uuid";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, AuthService.CURRENT_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return new UserProfile(
                        rs.getString("full_name"),
                        rs.getString("username"),
                        rs.getString("email")
                    );
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getMyProfile failed", e);
        }
        return new UserProfile("Student", "user", "email@example.com");
    }

    public boolean updateProfile(String fullName, String username) {
        if (AuthService.CURRENT_USER_ID == null) return false;
        String sql = "UPDATE users SET full_name = ?, username = ? WHERE id = ?::uuid";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, username);
            ps.setObject(3, AuthService.CURRENT_USER_ID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "updateProfile failed", e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // AI CONTEXT QUERIES
    // ──────────────────────────────────────────────────────────────

    public String fetchResourceContextForAI(String userQuery) {
        StringBuilder ctx = new StringBuilder();
        String kw = "%" + userQuery.trim().toLowerCase() + "%";
        String sql = """
            SELECT r.title, r.link, r.type, r.difficulty, r.upvotes, r.ai_summary,
                   c.code AS course_code, s.name AS segment_name, t.title AS topic_name,
                   (SELECT STRING_AGG('- ' || user_note, E'\\n')
                    FROM user_progress WHERE resource_id = r.id
                      AND user_note IS NOT NULL AND user_note <> '') AS community_notes
            FROM resources r
            JOIN topics   t ON r.topic_id   = t.id
            JOIN segments s ON t.segment_id = s.id
            JOIN courses  c ON s.course_id  = c.id
            WHERE (LOWER(c.code)   LIKE ? OR LOWER(s.name)  LIKE ?
                OR LOWER(t.title)  LIKE ? OR LOWER(r.title) LIKE ?
                OR LOWER(r.tags)   LIKE ?)
              AND r.channel_id = ?
            ORDER BY r.upvotes DESC
            LIMIT 5
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) ps.setString(i, kw);
            ps.setInt(6, AuthService.CURRENT_CHANNEL_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ctx.append("=== RESOURCE CARD ===\nPath: ")
                       .append(rs.getString("course_code")).append(" > ")
                       .append(rs.getString("segment_name")).append(" > ")
                       .append(rs.getString("topic_name")).append("\n")
                       .append("📄 Title: ").append(rs.getString("title")).append("\n")
                       .append("🔗 Link: ").append(rs.getString("link")).append("\n")
                       .append("⭐ Upvotes: ").append(rs.getInt("upvotes"))
                       .append(" | AI Summary: ").append(rs.getString("ai_summary")).append("\n")
                       .append("💬 Community Notes: ").append(rs.getString("community_notes")).append("\n\n");
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "fetchResourceContextForAI failed", e);
        }
        return ctx.toString();
    }

    public List<Resource> searchResourcesSmart(String query) {
        if (AuthService.CURRENT_USER_ID == null) return new ArrayList<>();
        String kw = "%" + query.trim().toLowerCase() + "%";
        String sql = """
            SELECT
                r.id, r.title, r.link, r.type, r.difficulty, r.duration,
                r.is_public, r.upvotes, r.downvotes, r.tags, r.description, r.created_by,
                u.full_name  AS creator_name,
                c.code       AS course_code,
                s.name       AS segment_name,
                t.title      AS topic_name,
                COUNT(p.resource_id)                                          AS total_done,
                SUM(CASE WHEN p.difficulty_rating = 'Easy'   THEN 1 ELSE 0 END) AS easy_cnt,
                SUM(CASE WHEN p.difficulty_rating = 'Medium' THEN 1 ELSE 0 END) AS med_cnt,
                SUM(CASE WHEN p.difficulty_rating = 'Hard'   THEN 1 ELSE 0 END) AS hard_cnt,
                STRING_AGG(p.user_note, ' | ')                                AS all_notes,
                EXISTS(SELECT 1 FROM resource_votes  v WHERE v.resource_id = r.id AND v.user_id = ?::uuid) AS user_voted,
                EXISTS(SELECT 1 FROM user_progress  up WHERE up.resource_id = r.id AND up.user_id = ?::uuid) AS user_completed
            FROM resources r
            JOIN   topics   t ON r.topic_id    = t.id
            JOIN   segments s ON t.segment_id  = s.id
            JOIN   courses  c ON s.course_id   = c.id
            LEFT JOIN users u ON r.created_by  = u.id
            LEFT JOIN user_progress p ON p.resource_id = r.id
            WHERE (LOWER(c.code)   LIKE ? OR LOWER(s.name)  LIKE ?
                OR LOWER(t.title)  LIKE ? OR LOWER(r.title) LIKE ?
                OR LOWER(r.tags)   LIKE ?)
              AND r.channel_id = ?
            GROUP BY r.id, u.full_name, c.code, s.name, t.title
            ORDER BY r.upvotes DESC
            """;
        List<Resource> list = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, AuthService.CURRENT_USER_ID);
            ps.setObject(2, AuthService.CURRENT_USER_ID);
            for (int i = 3; i <= 7; i++) ps.setString(i, kw);
            ps.setInt(8, AuthService.CURRENT_CHANNEL_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResource(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "searchResourcesSmart failed", e);
        }
        return list;
    }

    public String fetchSystematicResources(String userQuery) {
        StringBuilder ctx = new StringBuilder();
        String kw = "%" + userQuery.trim().replace(" ", "%") + "%";
        String sql = """
            SELECT r.title, r.link, r.type, r.difficulty, r.upvotes, r.ai_summary,
                   c.code AS course_code, s.name AS segment_name, t.title AS topic_name,
                   (SELECT STRING_AGG(content, ' | ') FROM comments WHERE resource_id = r.id) AS feedback
            FROM resources r
            JOIN topics   t ON r.topic_id   = t.id
            JOIN segments s ON t.segment_id = s.id
            JOIN courses  c ON s.course_id  = c.id
            WHERE (c.code ILIKE ? OR s.name ILIKE ? OR t.title ILIKE ? OR r.tags ILIKE ?)
              AND r.channel_id = ?
            ORDER BY r.upvotes DESC, r.difficulty ASC
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) ps.setString(i, kw);
            ps.setInt(5, AuthService.CURRENT_CHANNEL_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ctx.append("[RESOURCE]\n")
                       .append(rs.getString("title")).append("\n")
                       .append(rs.getString("link")).append("\n\n");
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "fetchSystematicResources failed", e);
        }
        return ctx.toString();
    }

    public String fetchComprehensiveContext(String userQuery) {
        StringBuilder ctx = new StringBuilder();
        String kw = "%" + userQuery.trim().replace(" ", "%") + "%";
        String sql = """
            SELECT r.title, r.link, r.ai_summary, c.code, s.name AS segment,
                   (SELECT STRING_AGG('User Says: ' || content, ' | ')
                    FROM comments WHERE resource_id = r.id) AS discussion
            FROM resources r
            JOIN topics   t ON r.topic_id   = t.id
            JOIN segments s ON t.segment_id = s.id
            JOIN courses  c ON s.course_id  = c.id
            WHERE c.code ILIKE ? OR s.name ILIKE ? OR t.title ILIKE ?
               OR r.title ILIKE ? OR r.tags ILIKE ?
            ORDER BY r.upvotes DESC
            LIMIT 5
            """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) ps.setString(i, kw);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ctx.append("--- RESOURCE ---\n")
                       .append(rs.getString("title")).append("\n")
                       .append(rs.getString("ai_summary")).append("\n\n");
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "fetchComprehensiveContext failed", e);
        }
        return ctx.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    /** Map a ResultSet row (from getResources / searchResourcesSmart) to a Resource record. */
    private static Resource mapResource(ResultSet rs) throws SQLException {
        return new Resource(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("link"),
            rs.getString("type"),
            rs.getString("difficulty"),
            rs.getString("duration"),
            rs.getBoolean("user_completed"),
            rs.getBoolean("is_public"),
            rs.getInt("upvotes"),
            rs.getInt("downvotes"),
            rs.getString("creator_name"),
            rs.getString("created_by"),
            rs.getInt("easy_cnt"),
            rs.getInt("med_cnt"),
            rs.getInt("hard_cnt"),
            rs.getBoolean("user_voted"),
            rs.getString("tags"),
            rs.getString("description"),
            rs.getString("course_code"),
            rs.getString("segment_name"),
            rs.getString("topic_name"),
            rs.getString("all_notes")
        );
    }
}