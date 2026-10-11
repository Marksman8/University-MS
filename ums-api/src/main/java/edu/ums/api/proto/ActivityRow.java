package edu.ums.api.proto;

import java.time.Instant;

/** One activity-log entry (prototype). */
public record ActivityRow(
    long id,
    Instant occurredAt,
    String username,
    String role,
    String action,
    String entity,
    String outcome,
    String details) {}
