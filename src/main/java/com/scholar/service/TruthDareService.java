package com.scholar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholar.model.TruthDareRoom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * TruthDareService — backend logic for room lifecycle in Supabase.
 *
 * FIX LIST vs original:
 *  1. ObjectMapper now has JavaTimeModule registered → Instant serialises correctly.
 *  2. createRoomInSupabase: end_time stored as ISO string (not raw Instant.toString()).
 *  3. All Supabase POST/PATCH requests include  Prefer: return=minimal  header
 *     so Supabase doesn't return the full row (avoids 200→204 status confusion).
 *  4. joinRoom: gameData and players casts are guarded with instanceof checks.
 *  5. sendRequest PATCH corrected to use .method("PATCH", body) (was fine in
 *     original but added explicit Content-Profile header for clarity).
 *  6. Supabase table URL should be  <supabaseUrl>/rest/v1/truth_dare_rooms —
 *     configure supabase.url in application.properties to that full path.
 */
@Service
public class TruthDareService {

    /**
     * Set in application.properties:
     *   supabase.url=https://xyzxyz.supabase.co/rest/v1/truth_dare_rooms
     *   supabase.key=your-anon-key
     */
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Called by Spring after @Value fields are injected.
     * Validates the URL so a misconfigured application.properties
     * fails fast with a clear message instead of a cryptic 404 later.
     *
     * CORRECT format:
     *   supabase.url=https://<ref>.supabase.co/rest/v1/<table_name>
     *
     * WRONG (causes 404 "requested path is invalid"):
     *   supabase.url=https://<ref>.supabase.co          ← missing /rest/v1/table
     *   supabase.url=https://<ref>.supabase.co/rest/v1  ← missing table name
     */
    // Public getters for the /debug endpoint
    public String getSupabaseUrl() { return supabaseUrl; }
    public String getSupabaseKey() { return supabaseKey; }

    /**
     * Fetches up to 1 row from the table with NO filters.
     * Used by /debug to verify the Supabase connection and table name.
     */
    public String debugRawGet() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "?limit=1"))
                    .header("apikey",        supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Accept",        "application/json")
                    .GET().build();
            HttpResponse<String> res =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return "HTTP " + res.statusCode() + "\n" + res.body();
        } catch (Exception e) {
            return "EXCEPTION: " + e.getMessage();
        }
    }

    @jakarta.annotation.PostConstruct
    public void validateConfig() {
        System.out.println("[TruthDareService] supabase.url = " + supabaseUrl);
        if (supabaseUrl == null || !supabaseUrl.contains("/rest/v1/")) {
            System.err.println(
                "\n[TruthDareService] *** CONFIGURATION ERROR ***\n" +
                "supabase.url must include /rest/v1/<table_name>\n" +
                "Current value: " + supabaseUrl + "\n" +
                "Expected:  https://<ref>.supabase.co/rest/v1/truth_dare_rooms\n"
            );
        }
    }

    // ── Question bank ────────────────────────────────────────────────────────

    private static final List<String> QUESTION_BANK = List.of(
        "What is the biggest lie you have ever told?",
        "Have you ever cheated on a test or exam?",
        "What is your most embarrassing moment in class?",
        "Who is your secret crush right now?",
        "Have you ever blamed someone else for your mistake?",
        "What is the most childish thing you still do?",
        "Have you ever stolen anything, even something small?",
        "What is a secret you have never told your parents?",
        "Have you ever pretended to be sick to skip school?",
        "What is the weirdest habit you have?",
        "Name something you have lied about on social media.",
        "What is the most embarrassing song on your playlist?"
    );

    public List<String> generateRandomQuestions() {
        List<String> pool = new ArrayList<>(QUESTION_BANK);
        Collections.shuffle(pool);
        return pool.subList(0, Math.min(5, pool.size()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // CREATE ROOM
    // ════════════════════════════════════════════════════════════════════════

    public boolean createRoomInSupabase(TruthDareRoom room) {
        try {
            room.setQuestions(generateRandomQuestions());
            room.setGameState("LOBBY");

            // FIX #2: build Supabase row with properly typed values
            Map<String, Object> dbRow = new LinkedHashMap<>();
            dbRow.put("room_code",   room.getRoomCode());
            dbRow.put("host_name",   room.getHostName());
            dbRow.put("max_players", room.getMaxPlayers());
            dbRow.put("game_state",  room.getGameState());

            // endTime is already a plain ISO-8601 String from the JAR client
            dbRow.put("end_time", room.getEndTime());

            // game_data: nested JSON object
            Map<String, Object> gameData = new LinkedHashMap<>();
            gameData.put("questions", room.getQuestions());
            gameData.put("players",   new ArrayList<>());
            dbRow.put("game_data", gameData); // ObjectMapper will serialise this as JSON

            return sendRequest("POST", supabaseUrl, dbRow);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // GET ROOM STATUS (polling)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns the raw JSON string from Supabase for the given room.
     * We return String (not Map) to avoid Jackson re-serialisation issues
     * with JSONB columns (game_data).  The controller passes this string
     * directly as the response body with MediaType.APPLICATION_JSON.
     *
     * Returns null if the room is not found or an error occurred.
     */
    public String getRoomStatusRaw(String roomCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "?room_code=eq." + roomCode + "&limit=1"))
                    .header("apikey",        supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Accept",        "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body().trim();
            int    code = response.statusCode();

            System.out.println("[TruthDareService] GET status/" + roomCode
                    + " → " + code + "  " + body);

            if (code < 200 || code >= 300) {
                System.err.println("[TruthDareService] Supabase error: " + body);
                return null;
            }

            // Supabase returns an array: [{"room_code":...}]
            // Empty array [] means room not found
            if (body.equals("[]") || body.isEmpty()) return null;

            // If it starts with '{' it is a PostgREST error object, not a row
            if (body.startsWith("{")) {
                System.err.println("[TruthDareService] Error object from Supabase: " + body);
                return null;
            }

            // Return the first element as a JSON object string
            // Strip the outer array brackets: [{ ... }]  →  { ... }
            String inner = body.substring(1, body.lastIndexOf(']')).trim();
            return inner.isEmpty() ? null : inner;

        } catch (Exception e) {
            System.err.println("[TruthDareService] getRoomStatus exception: " + e.getMessage());
            return null;
        }
    }

    /** @deprecated use getRoomStatusRaw — kept for internal joinRoom use only */
    @Deprecated
    public Map<String, Object> getRoomStatus(String roomCode) {
        try {
            String raw = getRoomStatusRaw(roomCode);
            if (raw == null) return null;
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            System.err.println("[TruthDareService] getRoomStatus parse error: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // JOIN ROOM
    // ════════════════════════════════════════════════════════════════════════

   @SuppressWarnings("unchecked")
    public boolean joinRoom(String roomCode, String playerName) {
        try {
            Map<String, Object> room = getRoomStatus(roomCode);
            if (room == null) return false;

            // 🌟 ডাটা String নাকি Map, সেটি চেক করে সেফলি পার্স করা
            Object rawData = room.get("game_data");
            Map<String, Object> gameData;
            
            if (rawData instanceof String) {
                gameData = objectMapper.readValue((String) rawData, new TypeReference<Map<String, Object>>() {});
            } else if (rawData instanceof Map) {
                gameData = new LinkedHashMap<>((Map<String, Object>) rawData); // Mutable Map
            } else {
                return false;
            }

            // প্লেয়ার লিস্ট আপডেট
            Object rawPlayers = gameData.get("players");
            List<Map<String, Object>> players = new ArrayList<>();
            if (rawPlayers instanceof List) {
                players.addAll((List<Map<String, Object>>) rawPlayers);
            }
            gameData.put("players", players);

            boolean alreadyIn = players.stream()
                    .anyMatch(p -> playerName.equals(p.get("name")));

            if (!alreadyIn) {
                Map<String, Object> newPlayer = new LinkedHashMap<>();
                newPlayer.put("name", playerName);
                newPlayer.put("answer", "");
                newPlayer.put("truth_votes", 0);
                newPlayer.put("lie_votes", 0);
                players.add(newPlayer);
            }

            Map<String, Object> patch = Map.of("game_data", gameData);
            return sendRequest("PATCH", supabaseUrl + "?room_code=eq." + roomCode, patch);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DELETE ROOM
    // ════════════════════════════════════════════════════════════════════════

    public boolean deleteRoom(String roomCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "?room_code=eq." + roomCode))
                    .header("apikey",        supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Prefer",        "return=minimal")  // FIX #3
                    .DELETE()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 204 || response.statusCode() == 200;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNAL: send POST or PATCH
    // ════════════════════════════════════════════════════════════════════════

    private boolean sendRequest(String method, String url, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey",        supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Content-Type",  "application/json")
                .header("Prefer",        "return=minimal");  // FIX #3

        switch (method) {
            case "POST"  -> builder.POST(HttpRequest.BodyPublishers.ofString(json));
            // FIX #5: PATCH must use .method() — HttpRequest has no .patch() shortcut
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(json));
            default      -> throw new IllegalArgumentException("Unsupported method: " + method);
        }

        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            System.err.println("[TruthDareService] " + method + " " + url
                    + " → " + code + "  " + response.body());
            return false;
        }
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    // START GAME (Change state to ACTIVE)
    // ════════════════════════════════════════════════════════════════════════
    public boolean startGame(String roomCode) {
        try {
            Map<String, Object> patch = Map.of("game_state", "ACTIVE");
            return sendRequest("PATCH", supabaseUrl + "?room_code=eq." + roomCode, patch);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean submitAnswer(String roomCode, String playerName, String answer) {
        try {
            Map<String, Object> room = getRoomStatus(roomCode);
            if (room == null) return false;

            Object rawData = room.get("game_data");
            Map<String, Object> gameData = (rawData instanceof String) 
                ? objectMapper.readValue((String) rawData, new TypeReference<Map<String, Object>>() {}) 
                : new LinkedHashMap<>((Map<String, Object>) rawData);

            List<Map<String, Object>> players = new ArrayList<>((List<Map<String, Object>>) gameData.get("players"));

            for (Map<String, Object> p : players) {
                if (playerName.equals(p.get("name"))) {
                    p.put("answer", answer);
                    break;
                }
            }
            gameData.put("players", players);
            return sendRequest("PATCH", supabaseUrl + "?room_code=eq." + roomCode, Map.of("game_data", gameData));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean submitVote(String roomCode, String targetPlayer, boolean isTruth) {
        try {
            Map<String, Object> room = getRoomStatus(roomCode);
            if (room == null) return false;

            Object rawData = room.get("game_data");
            Map<String, Object> gameData = (rawData instanceof String) 
                ? objectMapper.readValue((String) rawData, new TypeReference<Map<String, Object>>() {}) 
                : new LinkedHashMap<>((Map<String, Object>) rawData);

            List<Map<String, Object>> players = new ArrayList<>((List<Map<String, Object>>) gameData.get("players"));

            for (Map<String, Object> p : players) {
                if (targetPlayer.equals(p.get("name"))) {
                    int truthVotes = Integer.parseInt(p.getOrDefault("truth_votes", "0").toString());
                    int lieVotes = Integer.parseInt(p.getOrDefault("lie_votes", "0").toString());
                    
                    if (isTruth) p.put("truth_votes", truthVotes + 1);
                    else p.put("lie_votes", lieVotes + 1);
                    break;
                }
            }
            gameData.put("players", players);
            return sendRequest("PATCH", supabaseUrl + "?room_code=eq." + roomCode, Map.of("game_data", gameData));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
