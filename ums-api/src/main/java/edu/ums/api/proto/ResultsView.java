package edu.ums.api.proto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Results of the latest published term; GPA fields are null when nothing is published (prototype).
 */
public record ResultsView(
    String termName,
    List<ResultRow> results,
    BigDecimal sgpa,
    BigDecimal cgpa,
    int creditsEarned) {}
