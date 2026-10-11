package edu.ums.api.proto;

import java.math.BigDecimal;

/** One published course result (prototype). */
public record ResultRow(
    String courseCode,
    String courseTitle,
    int credits,
    BigDecimal totalPct,
    String grade,
    BigDecimal gradePoint) {}
