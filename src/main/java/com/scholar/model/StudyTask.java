package com.scholar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StudyTask(
    String id,
    String title,
    String date,
    String startTime,
    Integer durationMinutes,
    String roomNo,
    String type,
    String tags,        
    String creatorRole,
    String ctCourse,    
    String ctSyllabus,
    String status,      // 🌟 NEW: "PENDING", "COMPLETED", "BACKLOG"
    String importance   // 🌟 NEW: "High", "Medium", "Low"
) {}