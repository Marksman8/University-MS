package edu.ums.api.proto;

import java.math.BigDecimal;

/** A mark to save; value is ignored when absent (prototype). */
public record MarkEntry(long enrolmentId, long componentId, BigDecimal value, boolean absent) {}
