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
 * ResourceService — legacy wrapper for basic resource operations.
 *
 * NOTE: For community/topic resources, use CourseService instead.
 * This service handles only simple resources table access used by DashboardController.
 *
 * Schema fix: resources table uses `description` as the text column for desc.
 *             `user_notes` is also written on insert for RAG keyword fallback.
 *
 * Path: src/main/java/com/scholar/service/ResourceService.java
 */
@Service
public class ResourceService {

    private static final Logger LOG = Logger.getLogger(ResourceService.class.getName());

    @Autowired private DataSource dataSource;

    /** Optional — app starts fine without RAG configured. */
    @Autowired(required = false)
    private RAGService ragService;

    private Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    public record Resource(int id, String title, String link, String type, String content) {}

    /**
     * Adds a resource (used for AI Guide generation from DashboardController).
     *
     * Inserts into: title, link, type, description, user_notes, upvotes, downvotes,
     *               is_public, is_completed, view_count, download_count, position_order
     * Then async-indexes via RAGService.
     */
    public boolean addResource(String title, String link, String type, String content) {
        if (title == null || title.isBlank()) return false;

        String sql = """
            INSERT INTO resources
                (title, link, type, description, user_notes,
                 upvotes, downvotes, is_public, is_completed,
                 view_count, download_count, position_order)
            VALUES (?, ?, ?, ?, ?, 0, 0, true, false, 0, 0, 0)
            RETURNING id
            """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title.trim());
            ps.setString(2, link != null ? link.trim() : "");
            ps.setString(3, type != null ? type : "LINK");
            ps.setString(4, content != null ? content : "");
            ps.setString(5, content != null ? content : ""); // user_notes mirrors description for RAG

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long newId = rs.getLong("id");
                    if (ragService != null) {
                        final String textToIndex = title.trim() + ". " + (content != null ? content : "");
                        Thread t = new Thread(() -> {
                            try {
                                ragService.indexResource(newId, textToIndex);
                                LOG.info("RAG: indexed resource id=" + newId);
                            } catch (Exception ex) {
                                LOG.log(Level.WARNING, "RAG indexing failed (non-critical) for id=" + newId, ex);
                            }
                        }, "rag-indexer-" + newId);
                        t.setDaemon(true);
                        t.start();
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "addResource failed", e);
        }
        return false;
    }

    public List<Resource> getAllResources() {
        List<Resource> list = new ArrayList<>();
        String sql = "SELECT id, title, link, type, description FROM resources ORDER BY id DESC";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                list.add(new Resource(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("link"),
                    rs.getString("type"),
                    rs.getString("description")
                ));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "getAllResources failed", e);
        }
        return list;
    }

    public boolean deleteResource(int id) {
        String sql = "DELETE FROM resources WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "deleteResource failed for id=" + id, e);
            return false;
        }
    }
}