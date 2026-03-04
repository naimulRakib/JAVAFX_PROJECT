package com.scholar.model;
public record AnswerReply(
    String id, String answerId, String userId, String userName, String content, String createdAt,
    String profilePictureUrl // 🌟 NEW
) {}