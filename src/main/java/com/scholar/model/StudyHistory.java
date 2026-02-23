package com.scholar.model;

/**
 * STUDY HISTORY MODEL — Updated for UUID and Analytics
 * Path: src/main/java/com/scholar/model/StudyHistory.java
 */
public record StudyHistory(
    String id,
    String userId,
    String topic,
    String task,
    int plannedTime,
    int completedTime,
    int earnedXp,         // XP হিসেব রাখার জন্য
    String date
) {}