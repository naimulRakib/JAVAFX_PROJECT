package com.scholar.model;

/**
 * A comment on a DailyThread post.
 */
public record ThreadComment(
    String id,
    String threadId,
    String userId,
    String authorName,
    String content,
    String createdAt
) {}