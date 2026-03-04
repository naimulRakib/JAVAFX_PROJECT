package com.scholar.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TruthDareRoom — data model for the game room.
 *
 * FIX: endTime changed from Instant to String.
 *
 * Root cause of the 400 error:
 *   Instant.toString() produces "2026-02-27T08:54:37.114179Z" — note the
 *   microseconds (.114179).  Spring Boot's default Jackson config does not
 *   have JavaTimeModule registered, so it cannot parse Instant at all.
 *   Even with JavaTimeModule, the nanosecond precision from Instant.now()
 *   breaks the fixed-pattern @JsonFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").
 *
 *   Solution: store endTime as a plain String.  The JAR already builds it
 *   with Instant.toString() which is valid ISO-8601.  The service stores it
 *   as a string in Supabase (timestamptz accepts ISO strings).  No Jackson
 *   type conversion needed anywhere in the chain.
 *
 * @JsonProperty  = canonical name used when WRITING to Supabase (snake_case)
 * @JsonAlias     = extra names accepted when READING from JAR (camelCase)
 */
public class TruthDareRoom {

    @JsonProperty("room_code")
    @JsonAlias("roomCode")
    private String roomCode;

    @JsonProperty("host_name")
    @JsonAlias("hostName")
    private String hostName;

    @JsonProperty("max_players")
    @JsonAlias("maxPlayers")
    private int maxPlayers;

    @JsonProperty("game_state")
    @JsonAlias("gameState")
    private String gameState;

    /**
     * Stored and transported as an ISO-8601 string, e.g. "2026-02-27T09:00:00Z".
     * No Instant/JavaTimeModule needed — avoids the HttpMessageNotReadableException.
     */
    @JsonProperty("end_time")
    @JsonAlias("endTime")
    private String endTime;

    @JsonProperty("questions")
    private List<String> questions;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getRoomCode()              { return roomCode; }
    public void   setRoomCode(String v)      { this.roomCode = v; }

    public String getHostName()              { return hostName; }
    public void   setHostName(String v)      { this.hostName = v; }

    public int    getMaxPlayers()            { return maxPlayers; }
    public void   setMaxPlayers(int v)       { this.maxPlayers = v; }

    public String getGameState()             { return gameState; }
    public void   setGameState(String v)     { this.gameState = v; }

    public String getEndTime()               { return endTime; }
    public void   setEndTime(String v)       { this.endTime = v; }

    public List<String> getQuestions()       { return questions; }
    public void setQuestions(List<String> v) { this.questions = v; }
}