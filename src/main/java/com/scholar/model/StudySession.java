package com.scholar.model;

/**
 * STUDY SESSION MODEL — Updated for UUID
 * Path: src/main/java/com/scholar/model/StudySession.java
 */
public record StudySession(
    String id,
    String roomId,
    String userId,
    String userName,
    String topic,
    String taskDescription,
    int timerDuration,       // minutes
    String startTime,
    String completionStatus  // STUDYING | COMPLETED | ABANDONED
) {}