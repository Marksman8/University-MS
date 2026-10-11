package edu.ums.proto.audit.repository;

import edu.ums.api.proto.ActivityRow;
import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** JDBC {@link ActivityRepository}. */
// Complexity: time O(1) per insert; O(log n + k) per feed read (PK index), k <= limit <= 200;
// space O(k)
public final class JdbcActivityRepository extends BaseRepository implements ActivityRepository {

  private static final String COLUMNS =
      "id, occurred_at, username, role, action,"
          + " coalesce(entity_type || ':' || entity_id, entity_type) AS entity, outcome,"
          + " details::text AS details";

  /** Creates the repository. */
  public JdbcActivityRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public void insert(
      Long userId,
      String username,
      String role,
      UUID sessionId,
      String action,
      String entityType,
      String entityId,
      String outcome,
      String detailsJson) {
    update(
        "INSERT INTO activity_log (user_id, username, role, session_id, action, entity_type,"
            + " entity_id, outcome, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
        ps -> {
          if (userId == null) {
            ps.setNull(1, Types.BIGINT);
          } else {
            ps.setLong(1, userId);
          }
          ps.setString(2, username);
          ps.setString(3, role);
          ps.setObject(4, sessionId);
          ps.setString(5, action);
          ps.setString(6, entityType);
          ps.setString(7, entityId);
          ps.setString(8, outcome);
          ps.setString(9, detailsJson);
        });
  }

  @Override
  public List<ActivityRow> after(long afterId, int limit) {
    return queryList(
        "SELECT " + COLUMNS + " FROM activity_log WHERE id > ? ORDER BY id LIMIT ?",
        ps -> {
          ps.setLong(1, afterId);
          ps.setInt(2, limit);
        },
        JdbcActivityRepository::map,
        limit);
  }

  @Override
  public List<ActivityRow> latest(int limit) {
    List<ActivityRow> rows =
        new ArrayList<>(
            queryList(
                "SELECT " + COLUMNS + " FROM activity_log ORDER BY id DESC LIMIT ?",
                ps -> ps.setInt(1, limit),
                JdbcActivityRepository::map,
                limit));
    Collections.reverse(rows);
    return Collections.unmodifiableList(rows);
  }

  private static ActivityRow map(ResultSet rs) throws SQLException {
    return new ActivityRow(
        rs.getLong("id"),
        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
        rs.getString("username"),
        rs.getString("role"),
        rs.getString("action"),
        rs.getString("entity"),
        rs.getString("outcome"),
        rs.getString("details"));
  }
}
