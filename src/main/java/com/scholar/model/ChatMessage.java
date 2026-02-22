package com.scholar.model;

import java.time.LocalDateTime;

public record ChatMessage(
    String id,
    String senderId,
    String senderName,
    String content,
    String timestamp,
    boolean isMe,
    boolean isAdmin
) {}