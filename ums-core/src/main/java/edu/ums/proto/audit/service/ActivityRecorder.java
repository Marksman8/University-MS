package edu.ums.proto.audit.service;

import edu.ums.api.proto.ActivityRow;
import edu.ums.proto.shared.Actor;
import java.util.List;
import java.util.Map;

/**
 * Writes the activity log. Prototype simplification: {@link #success} writes synchronously in the
 * caller's transaction; {@link #failure} writes in its own short transaction after the failed call
 * rolled back. Details never contain passwords or hashes.
 */
public interface ActivityRecorder {

  /** Records a successful action in the current transaction. */
  void success(
      Actor actor, String action, String entityType, Object entityId, Map<String, ?> details);

  /**
   * Records a failed or denied action: in a new transaction after a rollback, or joined to the
   * current one when it is still active (login). Best effort: a failure to write is logged and
   * never thrown.
   *
   * @param actor the signed-in user, or {@code null} when unknown (role ANONYMOUS)
   * @param username the attempted username when {@code actor} is null; truncated to 64 characters
   */
  void failure(Actor actor, String username, String action, Map<String, ?> details);

  /** Records a successful action by the system (seed, bootstrap) in the current transaction. */
  void system(String action, String entityType, Object entityId, Map<String, ?> details);

  /** Entries newer than {@code afterId} ({@code <= 0}: the latest 200), oldest first. */
  List<ActivityRow> feed(long afterId);
}
