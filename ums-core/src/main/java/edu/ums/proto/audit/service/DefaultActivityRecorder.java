package edu.ums.proto.audit.service;

import edu.ums.api.proto.ActivityRow;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.UmsException;
import edu.ums.proto.audit.repository.ActivityRepository;
import edu.ums.proto.shared.Actor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Synchronous {@link ActivityRecorder} (prototype; P1 replaces it with an async writer). */
// Complexity: time O(d) per record, d = detail entries (<= a handful); space O(d)
public final class DefaultActivityRecorder implements ActivityRecorder {

  /** Page size of the feed. */
  public static final int FEED_LIMIT = 200;

  private static final Logger LOG = LoggerFactory.getLogger(DefaultActivityRecorder.class);
  private static final int MAX_USERNAME = 64;
  private static final int MAX_DETAIL = 200;

  private final ActivityRepository repository;
  private final TransactionRunner transactions;

  /** Creates the recorder. */
  public DefaultActivityRecorder(ActivityRepository repository, TransactionRunner transactions) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
  }

  @Override
  public void success(
      Actor actor, String action, String entityType, Object entityId, Map<String, ?> details) {
    repository.insert(
        actor.userId(),
        actor.username(),
        actor.role().name(),
        actor.sessionId(),
        action,
        entityType,
        entityId == null ? null : String.valueOf(entityId),
        "SUCCESS",
        json(details));
  }

  @Override
  public void failure(Actor actor, String username, String action, Map<String, ?> details) {
    try {
      transactions.inTransaction(
          false,
          () -> {
            repository.insert(
                actor == null ? null : actor.userId(),
                actor == null ? truncate(username) : actor.username(),
                actor == null ? "ANONYMOUS" : actor.role().name(),
                actor == null ? null : actor.sessionId(),
                action,
                null,
                null,
                "FAILURE",
                json(details));
            return null;
          });
    } catch (UmsException e) { // documented best-effort path: audit must not mask the real error
      LOG.warn("Could not record failed action {} ({})", action, e.code(), e);
    }
  }

  @Override
  public void system(String action, String entityType, Object entityId, Map<String, ?> details) {
    repository.insert(
        null,
        "system",
        "SYSTEM",
        null,
        action,
        entityType,
        entityId == null ? null : String.valueOf(entityId),
        "SUCCESS",
        json(details));
  }

  @Override
  public List<ActivityRow> feed(long afterId) {
    return afterId <= 0 ? repository.latest(FEED_LIMIT) : repository.after(afterId, FEED_LIMIT);
  }

  private static String truncate(String username) {
    if (username == null) {
      return null;
    }
    return username.length() <= MAX_USERNAME ? username : username.substring(0, MAX_USERNAME);
  }

  /** Small flat JSON object; values are stringified, escaped and bounded. */
  static String json(Map<String, ?> details) {
    if (details == null || details.isEmpty()) {
      return null;
    }
    StringBuilder out = new StringBuilder("{");
    new TreeMap<>(details)
        .forEach(
            (key, value) -> {
              if (out.length() > 1) {
                out.append(',');
              }
              appendString(out, key);
              out.append(':');
              appendString(out, String.valueOf(value));
            });
    return out.append('}').toString();
  }

  private static void appendString(StringBuilder out, String text) {
    String bounded = text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL);
    out.append('"');
    for (int i = 0; i < bounded.length(); i++) {
      char c = bounded.charAt(i);
      if (c == '"' || c == '\\') {
        out.append('\\').append(c);
      } else if (c < 0x20) {
        out.append(String.format("\\u%04x", (int) c));
      } else {
        out.append(c);
      }
    }
    out.append('"');
  }
}
