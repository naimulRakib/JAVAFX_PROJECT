package com.scholar.controller;

import com.scholar.model.TruthDareRoom;
import com.scholar.service.TruthDareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TruthDareController — REST API for the Truth & Dare game rooms.
 *
 * Endpoints consumed by TruthDareOnline.java (JAR plugin):
 *   POST   /api/truthdare/create           — create room
 *   POST   /api/truthdare/join             — join room
 *   GET    /api/truthdare/status/{code}    — poll room state
 *   DELETE /api/truthdare/delete           — clean up after game
 *
 * FIX: @CrossOrigin("*") is necessary so the JavaFX HttpClient (which sends
 *      a plain HTTP request, not a browser) is not blocked by CORS.
 *      Also ensures WebView-based integrations work without 403.
 */
@RestController
@RequestMapping("/api/truthdare")
@CrossOrigin(origins = "*")
public class TruthDareController {

    @Autowired
    private TruthDareService gameService;

    // ════════════════════════════════════════════════════════════════════════
    // CREATE ROOM
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Body: { "roomCode":"RM-4729", "hostName":"Alice",
     *         "maxPlayers":10, "endTime":"2025-12-01T12:00:00Z" }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody TruthDareRoom room) {
        // Clearer per-field validation — helps diagnose JSON key mismatches
        if (room.getRoomCode() == null || room.getRoomCode().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("'roomCode' is missing. Send camelCase JSON: {\"roomCode\":\"RM-1234\",...}");
        }
        if (room.getHostName() == null || room.getHostName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("'hostName' is missing. Send camelCase JSON: {\"hostName\":\"Alice\",...}");
        }
        boolean ok = gameService.createRoomInSupabase(room);
        return ok
                ? ResponseEntity.ok(Map.of(
                        "status",   "created",
                        "roomCode", room.getRoomCode()))
                : ResponseEntity.internalServerError()
                        .body("Failed to create room in database.");
    }

    // ════════════════════════════════════════════════════════════════════════
    // JOIN ROOM
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Called by the JAR client after verifying the room exists via /status.
     *   POST /api/truthdare/join?roomCode=RM-4729&playerName=Bob
     */
@PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody Map<String, String> payload) {
        try {
            String roomCode = payload.get("roomCode");
            String playerName = payload.get("playerName");

            if (roomCode == null || playerName == null) {
                return ResponseEntity.badRequest().body("roomCode and playerName are required.");
            }

            boolean joined = gameService.joinRoom(roomCode, playerName);
            if (joined) {
                return ResponseEntity.ok(Map.of(
                        "status", "joined",
                        "roomCode", roomCode,
                        "playerName", playerName));
            } else {
                return ResponseEntity.badRequest().body("Room not found or could not join.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Server Error: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // POLL STATUS  (called every 3 seconds by the JAR client AnimationTimer)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns the full room row from Supabase including game_data (players,
     * questions) and game_state.  The JAR client parses this to update the
     * lobby player count and detect when game_state changes to "ACTIVE".
     */
    @GetMapping("/status/{roomCode}")
    public ResponseEntity<?> getStatus(@PathVariable String roomCode) {
        String raw = gameService.getRoomStatusRaw(roomCode);
        if (raw == null) return ResponseEntity.notFound().build();
        try {
            // Parse into JsonNode so Jackson serialises it back exactly as-is
            // This avoids double-encoding that happens with ResponseEntity<String>
            // when a @Primary ObjectMapper bean is present
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            // Fallback: return as plain string — should never happen if Supabase is healthy
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(raw);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DEBUG — hit this in browser to diagnose Supabase connection
    // http://localhost:8080/api/truthdare/debug
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/debug")
    public ResponseEntity<String> debug() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TruthDare Debug ===\n");

        // 1. Check config
        String url = gameService.getSupabaseUrl();
        String key = gameService.getSupabaseKey();
        sb.append("supabase.url = ").append(url).append("\n");
        sb.append("supabase.key starts with = ")
          .append(key != null && key.length() > 10 ? key.substring(0, 10) + "..." : key)
          .append("\n");
        sb.append("url has /rest/v1/ = ").append(url != null && url.contains("/rest/v1/")).append("\n");
        sb.append("url has table name = ")
          .append(url != null && url.contains("/rest/v1/") && url.length() > url.indexOf("/rest/v1/") + 9)
          .append("\n\n");

        // 2. Raw Supabase GET (list all rows, max 1)
        String raw = gameService.debugRawGet();
        sb.append("Raw Supabase GET response:\n").append(raw).append("\n");

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(sb.toString());
    }

    // ════════════════════════════════════════════════════════════════════════
    // DELETE ROOM
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Called by the host when the game ends or is cancelled.
     *   DELETE /api/truthdare/delete?roomCode=RM-4729
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteRoom(@RequestParam String roomCode) {
        if (roomCode.isBlank())
            return ResponseEntity.badRequest().body("roomCode is required.");
        boolean deleted = gameService.deleteRoom(roomCode);
        return deleted
                ? ResponseEntity.ok(Map.of("status", "deleted", "roomCode", roomCode))
                : ResponseEntity.internalServerError()
                
                .body("Could not delete room: " + roomCode);
    }


    // ════════════════════════════════════════════════════════════════════════
    // START GAME
    // ════════════════════════════════════════════════════════════════════════
    @PostMapping("/start")
    public ResponseEntity<?> startGame(@RequestBody Map<String, String> payload) {
        try {
            String roomCode = payload.get("roomCode");

            if (roomCode == null || roomCode.isBlank()) {
                return ResponseEntity.badRequest().body("roomCode is required.");
            }
            
            boolean started = gameService.startGame(roomCode);
            if (started) {
                return ResponseEntity.ok(Map.of("status", "started", "roomCode", roomCode));
            } else {
                return ResponseEntity.internalServerError().body("Could not start game in database.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Server Error: " + e.getMessage());
        }
    }


    @PostMapping("/answer")
    public ResponseEntity<?> submitAnswer(@RequestBody Map<String, String> payload) {
        boolean ok = gameService.submitAnswer(payload.get("roomCode"), payload.get("playerName"), payload.get("answer"));
        return ok ? ResponseEntity.ok(Map.of("status", "answered")) : ResponseEntity.badRequest().body("Error");
    }

    @PostMapping("/vote")
    public ResponseEntity<?> submitVote(@RequestBody Map<String, Object> payload) {
        boolean ok = gameService.submitVote((String) payload.get("roomCode"), (String) payload.get("targetPlayer"), (boolean) payload.get("isTruth"));
        return ok ? ResponseEntity.ok(Map.of("status", "voted")) : ResponseEntity.badRequest().body("Error");
    }
}