package com.scholar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RAGService — Retrieval-Augmented Generation core.
 *
 * Flow:
 * 1. Embed user query → float[] vector
 * 2. pgvector similarity search on resources using content_vector
 * 3. Fallback to keyword search on course_name/segment_name/topic_name/title/tags
 * 4. Fetch course structure for AI context
 * 5. Fetch user progress for personalisation
 * 6. Build rich system prompt → call OpenRouter → return HTML
 */
@Service
public class RAGService {

    private static final Logger LOG = Logger.getLogger(RAGService.class.getName());

    @Autowired private JdbcTemplate      jdbc;
    @Autowired private EmbeddingService  embedder;
    @Autowired private GeminiService     ai;
    @Autowired private CourseService     courseService;
    @Autowired private ResourceCacheService resourceCache;

    // ──────────────────────────────────────────────────────────────
    // PUBLIC: ANSWER WITH RAG
    // ──────────────────────────────────────────────────────────────

    public String answerWithRAG(String userQuery, String courseHint, UUID userId) {
        if (userQuery == null || userQuery.isBlank())
            return "<p style='color:#94a3b8;'>Please type a question.</p>";

        // STEP 1: Embed query
        float[] queryVector = embedder.embed(userQuery);
        String  vectorStr   = embedder.toVectorString(queryVector);

        // STEP 2: Vector similarity search
        List<Map<String, Object>> vectorResults = vectorSearch(vectorStr, courseHint);
        LOG.info("RAG vector search returned " + vectorResults.size() + " results for: " + userQuery);

        // STEP 3: Keyword/cache fallback if vector search empty
        String keywordContext = "";
        List<com.scholar.model.ResourceDoc> cacheHits = List.of();
        if (vectorResults.isEmpty()) {
            cacheHits = resourceCache.search(userQuery, courseHint, 6);
            if (!cacheHits.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (var d : cacheHits) sb.append(formatResourceDoc(d));
                keywordContext = sb.toString();
                LOG.info("RAG: using cache fallback, found " + cacheHits.size() + " items");
            } else {
                keywordContext = courseService.fetchResourceContextForAI(userQuery);
                LOG.info("RAG: using keyword fallback, found context length=" + keywordContext.length());
            }
        }

        // STEP 4: Course structure
        String courseStructure = getCourseStructure(courseHint);

        // STEP 5: User progress
        String userProgress = getUserProgress(userId);

        // STEP 6: Build context
        StringBuilder context = new StringBuilder();

        if (!vectorResults.isEmpty()) {
            context.append("=== MOST RELEVANT RESOURCES (vector search) ===\n");
            for (Map<String, Object> row : vectorResults) {
                context.append(formatResourceRow(row));
            }
        } else if (!keywordContext.isBlank()) {
            context.append("=== RELEVANT RESOURCES (keyword search) ===\n");
            context.append(keywordContext);
        } else {
            context.append("=== RESOURCES ===\n")
                   .append("No resources found for this query in the database.\n");
        }

        context.append("\n=== COURSE STRUCTURE ===\n").append(courseStructure);
        context.append("\n=== USER PROGRESS ===\n").append(userProgress);

        // STEP 7: Build system prompt and call AI
        return ai.chat(buildSystemPrompt(context.toString()), userQuery);
    }

    // ──────────────────────────────────────────────────────────────
    // PUBLIC: INDEX A RESOURCE
    // ──────────────────────────────────────────────────────────────

    /**
     * Embeds textContent and stores in resources.content_vector.
     * Called async from CourseService and ResourceService after insert/update.
     */
    public void indexResource(long resourceId, String textContent) {
        if (textContent == null || textContent.isBlank()) {
            LOG.warning("indexResource: blank text for id=" + resourceId + " — skipping");
            return;
        }
        try {
            float[] vector    = embedder.embed(textContent);
            String  vectorStr = embedder.toVectorString(vector);
            int updated = jdbc.update(
                "UPDATE resources SET content_vector = ?::vector WHERE id = ?",
                vectorStr, resourceId);
            if (updated > 0) {
                LOG.info("✅ RAGService: indexed resource id=" + resourceId);
            } else {
                LOG.warning("indexResource: no row updated for id=" + resourceId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "RAGService.indexResource failed for id=" + resourceId + " (non-critical)", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE: VECTOR SEARCH
    // ──────────────────────────────────────────────────────────────

    /**
     * pgvector cosine similarity search.
     * Queries course_name, segment_name, topic_name directly —
     * these are now always filled by CourseService.addDetailedResource().
     */
    private List<Map<String, Object>> vectorSearch(String vectorStr, String courseHint) {
        try {
            if (courseHint != null && !courseHint.isBlank()) {
                // Filter by course_name column (now always populated)
                return jdbc.queryForList("""
                    SELECT id, title, description, ai_summary, tags,
                           topic_name, segment_name, course_name,
                           type, link, difficulty, upvotes, community_notes,
                           1 - (content_vector <=> ?::vector) AS similarity
                    FROM resources
                    WHERE is_public = true
                      AND content_vector IS NOT NULL
                      AND (LOWER(course_name) LIKE '%' || LOWER(?) || '%'
                           OR LOWER(segment_name) LIKE '%' || LOWER(?) || '%'
                           OR LOWER(topic_name)   LIKE '%' || LOWER(?) || '%')
                    ORDER BY content_vector <=> ?::vector
                    LIMIT 6
                    """,
                    vectorStr, courseHint, courseHint, courseHint, vectorStr);
            } else {
                return jdbc.queryForList("""
                    SELECT id, title, description, ai_summary, tags,
                           topic_name, segment_name, course_name,
                           type, link, difficulty, upvotes, community_notes,
                           1 - (content_vector <=> ?::vector) AS similarity
                    FROM resources
                    WHERE is_public = true
                      AND content_vector IS NOT NULL
                    ORDER BY content_vector <=> ?::vector
                    LIMIT 6
                    """,
                    vectorStr, vectorStr);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "Vector search failed (pgvector enabled in Supabase?): " + e.getMessage());
            return List.of();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE: FORMAT RESOURCE ROW
    // ──────────────────────────────────────────────────────────────

    private String formatResourceRow(Map<String, Object> row) {
        double similarity = row.get("similarity") != null
            ? ((Number) row.get("similarity")).doubleValue() : 0.0;

        int id = row.get("id") != null ? ((Number) row.get("id")).intValue() : -1;
        var cached = id > 0 ? resourceCache.getById(id) : null;
        String avgTime = cached != null ? String.format("%.0f mins", cached.avgTimeMins()) : "—";
        String completionRate = cached != null && cached.progressCount() > 0
            ? String.format("%.0f%%", (cached.completedCount() * 100.0 / cached.progressCount()))
            : "—";

        String notes = safe(row.get("community_notes"));
        String noteLine = notes.equals("—") || notes.isBlank()
            ? ""
            : "   💬 Notes: " + notes + "\n";

        return String.format(
            "📚 [%s] %s  (relevance: %.0f%%)\n"
          + "   📍 %s > %s > %s\n"
          + "   📊 Type: %s | ⭐ Upvotes: %s | Difficulty: %s | ✅ Completion: %s | ⏱ Avg Time: %s\n"
          + "   🏷 Tags: %s\n"
          + "   🤖 Summary: %s\n"
          + "%s"
          + "   🔗 Link: %s\n\n",
            safe(row.get("type")),
            safe(row.get("title")),
            similarity * 100,
            safe(row.get("course_name")),
            safe(row.get("segment_name")),
            safe(row.get("topic_name")),
            safe(row.get("type")),
            safe(row.get("upvotes")),
            safe(row.get("difficulty")),
            completionRate,
            avgTime,
            safe(row.get("tags")),
            safe(row.get("ai_summary")),
            noteLine,
            safe(row.get("link"))
        );
    }

    private String formatResourceDoc(com.scholar.model.ResourceDoc d) {
        String completionRate = d.progressCount() > 0
            ? String.format("%.0f%%", (d.completedCount() * 100.0 / d.progressCount()))
            : "—";
        String avgTime = String.format("%.0f mins", d.avgTimeMins());
        String notes = safe(d.communityNotes());
        String noteLine = notes.equals("—") || notes.isBlank()
            ? ""
            : "   💬 Notes: " + notes + "\n";

        return String.format(
            "📚 [%s] %s\n"
          + "   📍 %s > %s > %s\n"
          + "   📊 Type: %s | ⭐ Upvotes: %d | Difficulty: %s | ✅ Completion: %s | ⏱ Avg Time: %s\n"
          + "   🏷 Tags: %s\n"
          + "   🤖 Summary: %s\n"
          + "%s"
          + "   🔗 Link: %s\n\n",
            safe(d.type()),
            safe(d.title()),
            safe(d.courseName()),
            safe(d.segmentName()),
            safe(d.topicName()),
            safe(d.type()),
            d.upvotes(),
            safe(d.difficulty()),
            completionRate,
            avgTime,
            safe(d.tags()),
            safe(d.aiSummary()),
            noteLine,
            safe(d.link())
        );
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE: COURSE STRUCTURE
    // ──────────────────────────────────────────────────────────────

    private String getCourseStructure(String courseHint) {
        if (courseHint == null || courseHint.isBlank())
            return "No course filter — showing all courses.\n";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.code, s.name AS segment, t.title AS topic
                FROM courses  c
                JOIN segments s ON s.course_id  = c.id
                JOIN topics   t ON t.segment_id = s.id
                WHERE c.code ILIKE '%' || ? || '%'
                ORDER BY s.sort_order, t.position_order
                LIMIT 30
                """, courseHint);
            if (rows.isEmpty()) return "No course structure found for: " + courseHint + "\n";
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : rows)
                sb.append("  [").append(r.get("segment")).append("] ")
                  .append(r.get("topic")).append("\n");
            return sb.toString();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "getCourseStructure failed (non-critical)", e);
            return "";
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE: USER PROGRESS
    // ──────────────────────────────────────────────────────────────

    private String getUserProgress(UUID userId) {
        if (userId == null) return "Guest user — no progress data.\n";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.title,
                       COALESCE(r.course_name, 'Unknown') AS course_name,
                       up.is_completed, up.difficulty_rating
                FROM user_progress up
                JOIN resources r ON r.id = up.resource_id
                WHERE up.user_id = ?
                ORDER BY up.created_at DESC
                LIMIT 10
                """, userId);
            if (rows.isEmpty()) return "No study progress recorded yet.\n";
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : rows)
                sb.append("  ").append(Boolean.TRUE.equals(r.get("is_completed")) ? "✅" : "🔄")
                  .append(" ").append(r.get("title"))
                  .append(" [").append(r.get("course_name")).append("]")
                  .append(" | ").append(safe(r.get("difficulty_rating"))).append("\n");
            return sb.toString();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "getUserProgress failed (non-critical)", e);
            return "Could not load user progress.\n";
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE: SYSTEM PROMPT
    // ──────────────────────────────────────────────────────────────

    private String buildSystemPrompt(String context) {
        return """
            You are ScholarGrid AI — a smart academic assistant for CSE university students.

            ══ STRICT RULES ══
            1. Answer ONLY using the DATABASE CONTEXT below.
            2. If the answer is not in the context, say:
               "I couldn't find this in your course materials. Try uploading more resources."
            3. NEVER make up links, topic names, or course codes.
            4. Always name which resource/topic you're referencing.
            5. When resources are found, list them as clickable HTML links.
            6. Prefer WEB-type resources when the user wants quick references or external reading.
            7. If asked for "best resources", rank by upvotes, then completion rate, then avg time (shorter is better).
            8. Always highlight resource stats (type, upvotes, difficulty, completion, avg time).
            9. Personalise using User Progress if available.
            10. Format as clean HTML: use <h3>, <ul>, <li>, <a href>, <strong>, <em>, <code>.
               Make it visually clear and easy to scan. NO raw markdown — only HTML tags.

            11. Dont use any emoji strictly!!

            ══ DATABASE CONTEXT ══
            %s
            ══════════════════════
            """.formatted(context);
    }

    private String safe(Object o) {
        return (o != null && !o.toString().equals("null")) ? o.toString() : "—";
    }



    // RAGService.java - এর ভেতরে এই মেথডটি যোগ করুন

    public void indexResource(int resourceId) {
        new Thread(() -> {
            try {
                String sql = "SELECT * FROM resources WHERE id = ?";
                Map<String, Object> row = jdbc.queryForMap(sql, resourceId);

                String aiContextText = String.format(
                    "Course: %s | Segment: %s | Topic: %s\n" +
                    "Resource Title: %s\n" +
                    "Type: %s | Difficulty: %s | Tags: %s\n" +
                    "Description: %s\n" +
                    "AI Summary: %s\n" +
                    "Community Notes: %s",
                    safe(row.get("course_name")), safe(row.get("segment_name")), safe(row.get("topic_name")),
                    safe(row.get("title")), 
                    safe(row.get("type")), safe(row.get("difficulty")), safe(row.get("tags")),
                    safe(row.get("description")), 
                    safe(row.get("ai_summary")),
                    safe(row.get("community_notes"))
                );

                float[] vector = embedder.embed(aiContextText);
                String vectorString = embedder.toVectorString(vector);

                jdbc.update("UPDATE resources SET content_vector = ?::vector WHERE id = ?", vectorString, resourceId);
                
                LOG.info("✅ Successfully Auto-Vectorized resource ID: " + resourceId);

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "❌ Failed to vectorize resource ID " + resourceId, e);
            }
        }).start();
    }
}
