package com.scholar.controller;

import com.scholar.service.RAGService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIChatController {

    @Autowired private RAGService ragService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> req) {
        String question  = req.getOrDefault("question", "");
        String course    = req.getOrDefault("course", null);   // e.g. "CSE 105"
        String userIdStr = req.getOrDefault("userId", null);

        UUID userId = null;
        try { if (userIdStr != null) userId = UUID.fromString(userIdStr); }
        catch (Exception ignored) {}

        String htmlResponse = ragService.answerWithRAG(question, course, userId);
        return ResponseEntity.ok(Map.of("html", htmlResponse));
    }



    @Autowired private JdbcTemplate jdbc;  
    @PostMapping("/backfill-vectors")
public ResponseEntity<String> backfillVectors() {
    new Thread(() -> {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, title, description, ai_summary, tags, type, difficulty " +
            "FROM resources WHERE content_vector IS NULL AND is_public = true LIMIT 200"
        );
        int count = 0;
        for (Map<String, Object> row : rows) {
            String text = row.getOrDefault("title", "") + ". "
                        + row.getOrDefault("ai_summary", "") + ". "
                        + row.getOrDefault("description", "") + ". "
                        + "Tags: " + row.getOrDefault("tags", "");
            ragService.indexResource((Long) row.get("id"), text.trim());
            count++;
            try { Thread.sleep(300); } catch (Exception ignored) {}
        }
        System.out.println("✅ Backfill done: " + count + " resources indexed");
    }, "rag-backfill").start();
    return ResponseEntity.ok("Backfill started — check console for progress");
}

}