package edu.ums.api.proto;

import java.math.BigDecimal;

/** Attendance per course; percent is null when no session counted (prototype). */
public record AttendanceSummary(
    String courseCode,
    String courseTitle,
    int present,
    int absent,
    int excused,
    BigDecimal percent) {}
