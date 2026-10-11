package edu.ums.api.proto;

import java.util.List;

/**
 * Result of a console query; sqlState and error are set (and rows empty) when the query failed
 * (prototype).
 */
public record QueryResult(
    List<String> columns,
    List<List<String>> rows,
    boolean truncated,
    long elapsedMillis,
    String sqlState,
    String error) {}
