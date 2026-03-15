package com.scholar.model;

public record ResourceDoc(
    int id,
    String title,
    String link,
    String type,
    String description,
    String tags,
    String aiSummary,
    String communityNotes,
    String courseName,
    String segmentName,
    String topicName,
    String difficulty,
    int upvotes,
    int downvotes,
    double avgTimeMins,
    int completedCount,
    int progressCount
) {}
