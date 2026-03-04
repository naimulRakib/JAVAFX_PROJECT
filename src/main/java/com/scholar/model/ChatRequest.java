package com.scholar.model;

/**
 * ChatRequest — a pending or accepted connection request.
 * avatarUrl is the Supabase profile_pic bucket public URL (nullable).
 * Path: src/main/java/com/scholar/model/ChatRequest.java
 */
public record ChatRequest(
        String id,
        String senderId,
        String senderName,
        String status,
        String avatarUrl        // ← profile_pic bucket URL (nullable)
) {
    /** Backward-compat constructor for code that doesn't pass avatarUrl yet. */
    public ChatRequest(String id, String senderId, String senderName, String status) {
        this(id, senderId, senderName, status, null);
    }
}