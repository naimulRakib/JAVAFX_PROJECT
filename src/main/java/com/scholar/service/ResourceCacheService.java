package com.scholar.service;

import com.scholar.model.ResourceDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResourceCacheService — in-memory cache of public resources + stats.
 * Keeps RAG fast and reduces DB round-trips on large datasets.
 */
@Service
public class ResourceCacheService {

    private static final long TTL_MS = 5 * 60 * 1000; // 5 minutes

    @Autowired private JdbcTemplate jdbc;

    private final ConcurrentHashMap<Integer, ResourceDoc> cache = new ConcurrentHashMap<>();
    private volatile long lastRefresh = 0L;

    @PostConstruct
    public void warmCache() {
        refreshAllAsync();
    }

    public List<ResourceDoc> getAll() {
        ensureFresh();
        return new ArrayList<>(cache.values());
    }

    public ResourceDoc getById(int id) {
        ensureFresh();
        return cache.get(id);
    }

    public void refreshAllAsync() {
        new Thread(this::refreshAll, "resource-cache-refresh").start();
    }

    public synchronized void refreshAll() {
        String sql = """
            SELECT r.id, r.title, r.link, r.type, r.description, r.tags, r.ai_summary,
                   r.community_notes,
                   r.course_name, r.segment_name, r.topic_name, r.difficulty,
                   r.upvotes, r.downvotes,
                   COALESCE(AVG(p.time_spent_mins) FILTER (WHERE p.is_completed),0) AS avg_time,
                   COALESCE(COUNT(p.*) FILTER (WHERE p.is_completed),0) AS completed_count,
                   COALESCE(COUNT(p.*),0) AS progress_count
            FROM resources r
            LEFT JOIN user_progress p ON p.resource_id = r.id
            WHERE r.is_public = true
            GROUP BY r.id
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        cache.clear();
        for (Map<String, Object> row : rows) {
            ResourceDoc doc = mapRow(row);
            cache.put(doc.id(), doc);
        }
        lastRefresh = System.currentTimeMillis();
    }

    public void refreshResource(int id) {
        String sql = """
            SELECT r.id, r.title, r.link, r.type, r.description, r.tags, r.ai_summary,
                   r.community_notes,
                   r.course_name, r.segment_name, r.topic_name, r.difficulty,
                   r.upvotes, r.downvotes,
                   COALESCE(AVG(p.time_spent_mins) FILTER (WHERE p.is_completed),0) AS avg_time,
                   COALESCE(COUNT(p.*) FILTER (WHERE p.is_completed),0) AS completed_count,
                   COALESCE(COUNT(p.*),0) AS progress_count
            FROM resources r
            LEFT JOIN user_progress p ON p.resource_id = r.id
            WHERE r.is_public = true AND r.id = ?
            GROUP BY r.id
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) {
            cache.remove(id);
        } else {
            ResourceDoc doc = mapRow(rows.get(0));
            cache.put(doc.id(), doc);
        }
        lastRefresh = System.currentTimeMillis();
    }

    public List<ResourceDoc> search(String query, String courseHint, int limit) {
        if (query == null || query.isBlank()) return List.of();
        ensureFresh();
        String q = query.toLowerCase();
        String course = courseHint != null ? courseHint.toLowerCase() : null;

        List<ResourceDoc> all = new ArrayList<>(cache.values());
        List<Scored> scored = new ArrayList<>();
        for (ResourceDoc d : all) {
            double score = 0.0;
            score += containsScore(d.title(), q, 3.0);
            score += containsScore(d.tags(), q, 2.0);
            score += containsScore(d.description(), q, 1.2);
            score += containsScore(d.aiSummary(), q, 1.0);
            score += containsScore(d.communityNotes(), q, 1.0);
            score += containsScore(d.topicName(), q, 1.5);
            score += containsScore(d.segmentName(), q, 1.5);
            score += containsScore(d.courseName(), q, 1.5);

            if (course != null && !course.isBlank()) {
                score += containsScore(d.courseName(), course, 2.0);
                score += containsScore(d.segmentName(), course, 1.0);
                score += containsScore(d.topicName(), course, 1.0);
            }

            // Add lightweight popularity signal
            score += (d.upvotes() - d.downvotes()) * 0.05;
            if (score > 0) scored.add(new Scored(d, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<ResourceDoc> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            out.add(scored.get(i).doc());
        }
        return out;
    }

    private void ensureFresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > TTL_MS) refreshAll();
    }

    private ResourceDoc mapRow(Map<String, Object> row) {
        return new ResourceDoc(
            ((Number) row.get("id")).intValue(),
            str(row.get("title")),
            str(row.get("link")),
            str(row.get("type")),
            str(row.get("description")),
            str(row.get("tags")),
            str(row.get("ai_summary")),
            str(row.get("community_notes")),
            str(row.get("course_name")),
            str(row.get("segment_name")),
            str(row.get("topic_name")),
            str(row.get("difficulty")),
            num(row.get("upvotes")),
            num(row.get("downvotes")),
            dbl(row.get("avg_time")),
            num(row.get("completed_count")),
            num(row.get("progress_count"))
        );
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static int num(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
    private static double dbl(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0.0; }

    private static double containsScore(String field, String q, double weight) {
        if (field == null) return 0.0;
        return field.toLowerCase().contains(q) ? weight : 0.0;
    }

    private record Scored(ResourceDoc doc, double score) {}
}
