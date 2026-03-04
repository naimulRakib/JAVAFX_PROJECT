package com.scholar.model;

/**
 * ChatMessage — used for both group and private messages.
 * avatarUrl is the Supabase profile_pic bucket public URL (nullable).
 * Path: src/main/java/com/scholar/model/ChatMessage.java
 */
public record ChatMessage(
        String  id,
        String  senderId,
        String  senderName,
        String  content,
        String  createdAt,
        boolean isMe,
        boolean isAdminMsg,
        String  avatarUrl       // ← profile_pic bucket URL (nullable)
) {
    /** Backward-compat constructor for code that doesn't pass avatarUrl yet. */
    public ChatMessage(String id, String senderId, String senderName,
                       String content, String createdAt,
                       boolean isMe, boolean isAdminMsg) {
        this(id, senderId, senderName, content, createdAt, isMe, isAdminMsg, null);
    }
}