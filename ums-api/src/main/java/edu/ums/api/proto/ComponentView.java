package edu.ums.api.proto;

import java.math.BigDecimal;

/** An assessment component (prototype). */
public record ComponentView(long id, String name, BigDecimal maxMarks, BigDecimal weightPct) {}
