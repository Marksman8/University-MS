package edu.ums.api.proto;

import java.math.BigDecimal;

/** One mark; value is null when not entered or absent (prototype). */
public record MarkCell(long componentId, BigDecimal value, boolean absent) {}
