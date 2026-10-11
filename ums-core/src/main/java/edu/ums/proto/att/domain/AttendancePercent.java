package edu.ums.proto.att.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Attendance percent = present ÷ (present + absent) × 100; excused excluded; scale 2 HALF_UP. */
public final class AttendancePercent {

  private AttendancePercent() {}

  /** Returns the percent, or {@code null} when no session counts. */
  public static BigDecimal of(int present, int absent) {
    int counted = present + absent;
    if (counted == 0) {
      return null;
    }
    return BigDecimal.valueOf(present * 100L)
        .divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
  }
}
