package com.scholar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Question(
    String topic,        // e.g., "Binary Search Tree"
    String text,         // The actual question
    String years,        // e.g., "2021, 2023, CT-2"
    int frequency,       // How many times it appeared
    int stars            // 1 to 5 based on importance
) {}