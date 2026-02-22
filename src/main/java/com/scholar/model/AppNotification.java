package com.scholar.model;

public record AppNotification(
    String id,
    String type,
    String title,
    String body,
    boolean isRead,
    String timeAgo
) {}
