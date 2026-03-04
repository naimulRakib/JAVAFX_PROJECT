package com.scholar.model;
public record PrivateContact(
        String userId,
        String userName,
        String avatarUrl         // ← NEW (nullable)
) {}