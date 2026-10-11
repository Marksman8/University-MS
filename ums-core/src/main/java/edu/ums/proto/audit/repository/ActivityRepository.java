package edu.ums.proto.audit.repository;

import edu.ums.api.proto.ActivityRow;
import java.util.List;
import java.util.UUID;

/** Persistence of {@code activity_log} for the prototype. Runs in the caller's transaction. */
public interface ActivityRepository {

  /** Inserts one entry; {@code userId}, {@code username}, {@code sessionId} may be null. */
  void insert(
      Long userId,
      String username,
      String role,
      UUID sessionId,
      String action,
      String entityType,
      String entityId,
      String outcome,
      String detailsJson);

  /** Entries with id greater than {@code afterId}, oldest first, at most {@code limit}. */
  List<ActivityRow> after(long afterId, int limit);

  /** The latest {@code limit} entries, oldest first. */
  List<ActivityRow> latest(int limit);
}
