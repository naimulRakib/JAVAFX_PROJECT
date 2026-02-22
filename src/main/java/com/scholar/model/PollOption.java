package com.scholar.model;
public record PollOption(String id, String text, int voteCount, boolean votedByMe) {}