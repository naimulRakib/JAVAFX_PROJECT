package com.scholar.model;

import java.time.LocalDate;

public record ClassOffPeriod(
    String id,
    LocalDate startDate,
    LocalDate endDate,
    String reason
) {}
