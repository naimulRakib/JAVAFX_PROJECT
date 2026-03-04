package com.scholar.model;

import java.util.List;

public record DailyThread(
    String id,
    String userId,
    String authorName,
    String contentText,
    String authorAvatar,   // 🌟 authorAvatar
    String mediaUrl,
    String photoUrl,
    String category,
    int likeCount,
    int dislikeCount,      // 🌟 NEW
    boolean likedByMe,     // 🌟 NEW
    boolean dislikedByMe,  // 🌟 NEW
    boolean savedByMe,
    String createdAt,
    List<ThreadComment> comments
) {
    // কাস্টম কনস্ট্রাক্টর (যেখানে comments লিস্ট ফাঁকা থাকবে)
    public DailyThread(
            String id, String userId, String authorName, String contentText, String authorAvatar, // ✅ authorAvatar ঠিক করা হয়েছে
            String mediaUrl, String photoUrl, String category,
            int likeCount, int dislikeCount, 
            boolean likedByMe, boolean dislikedByMe, boolean savedByMe, String createdAt) {
            
        // ক্যানোনিকাল কনস্ট্রাক্টর কল
        this(id, userId, authorName, contentText, authorAvatar, mediaUrl, photoUrl,
             category, likeCount, dislikeCount, likedByMe, dislikedByMe, savedByMe, createdAt, List.of());
    }
}