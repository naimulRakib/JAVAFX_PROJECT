package com.scholar.model;

import java.util.List;

/**
 * Represents a daily thread / post in the Social Zone feed.
 *
 * category values: PUBLIC | ACADEMICS | EVENTS | PLACEMENTS | ISSUES
 */
public record DailyThread(
    String id,
    String userId,
    String authorName,
    String contentText,
    String mediaUrl,
    String photoUrl,
    String category,
    int likeCount,
    boolean savedByMe,
    String createdAt,
    List<ThreadComment> comments
) {
    /** Convenience constructor — no comments loaded yet */
    public DailyThread(
            String id,
            String userId,
            String authorName,
            String contentText,
            String mediaUrl,
            String photoUrl,
            String category,
            int likeCount,
            boolean savedByMe,
            String createdAt) {
        this(id, userId, authorName, contentText, mediaUrl, photoUrl,
             category, likeCount, savedByMe, createdAt, List.of());
    }
}