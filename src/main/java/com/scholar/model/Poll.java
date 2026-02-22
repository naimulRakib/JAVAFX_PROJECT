package com.scholar.model;
import java.util.List;
public record Poll(String id, String question, String creatorName, List<PollOption> options, int totalVotes) {}