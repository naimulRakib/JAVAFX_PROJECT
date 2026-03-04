package com.scholar.model;
import java.util.List;
public record DoubtAnswer(
    String id, String doubtId, String mentorName, String mentorId, String content, boolean isBestAnswer,
    int upvotes, String createdAt, int mentorBestAnswerCount, List<AnswerReply> replies,
    String profilePictureUrl // 🌟 NEW
) {}