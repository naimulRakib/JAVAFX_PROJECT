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
    @Autowired private OpenRouterService ai;
    @Autowired private CourseService     courseService;

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

        // STEP 3: Keyword fallback if vector search empty
        String keywordContext = "";
        if (vectorResults.isEmpty()) {
            keywordContext = courseService.fetchResourceContextForAI(userQuery);
            LOG.info("RAG: using keyword fallback, found context length=" + keywordContext.length());
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
                           type, link, difficulty, upvotes,
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
                           type, link, difficulty, upvotes,
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

        return String.format(
            "📚 [%s] %s  (relevance: %.0f%%)\n"
          + "   📍 %s > %s > %s\n"
          + "   📊 Difficulty: %s | ⭐ Upvotes: %s\n"
          + "   🏷 Tags: %s\n"
          + "   🤖 Summary: %s\n"
          + "   🔗 Link: %s\n\n",
            safe(row.get("type")),
            safe(row.get("title")),
            similarity * 100,
            safe(row.get("course_name")),
            safe(row.get("segment_name")),
            safe(row.get("topic_name")),
            safe(row.get("difficulty")),
            safe(row.get("upvotes")),
            safe(row.get("tags")),
            safe(row.get("ai_summary")),
            safe(row.get("link"))
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
            6. If asked for "best resources", rank by upvotes and explain why each is good.
            7. Personalise using User Progress if available.
            8. Format as clean HTML: use <h3>, <ul>, <li>, <a href>, <strong>, <em>, <code>.
               Make it visually clear and easy to scan. NO raw markdown — only HTML tags.

            9. Dont use any emoji strictly!!

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

                // 🟢 FIX: safe(row.get("course_code"))
                String aiContextText = String.format(
                    "Course: %s | Segment: %s | Topic: %s\n" +
                    "Resource Title: %s\n" +
                    "Type: %s | Difficulty: %s | Tags: %s\n" +
                    "Description: %s\n" +
                    "AI Summary: %s",
                    safe(row.get("course_code")), safe(row.get("segment_name")), safe(row.get("topic_name")),
                    safe(row.get("title")), 
                    safe(row.get("type")), safe(row.get("difficulty")), safe(row.get("tags")),
                    safe(row.get("description")), 
                    safe(row.get("ai_summary"))
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