package com.scholar.model;
public record DoubtAnswer(String id, String doubtId, String mentorName, String mentorId, String content, boolean isBestAnswer, int upvotes, String createdAt) {}