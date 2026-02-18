package com.scholar.model;

public record RecurringClass(
    String courseName,
    String dayOfWeek, // "MONDAY", "TUESDAY", etc.
    String startTime, // "10:00"
    int durationMinutes,
    String roomNumber
) {}