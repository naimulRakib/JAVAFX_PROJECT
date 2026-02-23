package com.scholar.model;

/**
 * STUDY ROOM MODEL — Updated for UUID and UI Data
 * Path: src/main/java/com/scholar/model/StudyRoom.java
 */
public record StudyRoom(
    String id,
    String roomName,
    String type,          // PUBLIC | PRIVATE | DEPARTMENT
    String department,
    String createdBy,
    String creatorName,   // UI-তে দেখানোর জন্য
    String mode,          // SILENT | GROUP
    boolean activeStatus,
    int activeUsersCount  // বর্তমানে কতজন পড়ছে তার কাউন্ট
) {}