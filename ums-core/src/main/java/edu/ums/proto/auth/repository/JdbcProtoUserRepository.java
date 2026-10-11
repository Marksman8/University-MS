package edu.ums.proto.auth.repository;

import edu.ums.api.Role;
import edu.ums.api.proto.UserRow;
import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.StatementBinder;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.proto.auth.domain.LoginRow;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** JDBC {@link ProtoUserRepository}. */
// Complexity: time O(log n) per lookup/update (unique index / PK); O(k) per list, k <= 200;
// space O(k)
public final class JdbcProtoUserRepository extends BaseRepository implements ProtoUserRepository {

  /** One conditional update; an expired lock restarts the count at 1 (no read-then-write). */
  static final String RECORD_FAILURE =
      "UPDATE app_user"
          + " SET failed_attempts = CASE WHEN locked_until IS NOT NULL AND locked_until <= now()"
          + " THEN 1 ELSE failed_attempts + 1 END,"
          + " locked_until = CASE WHEN (CASE WHEN locked_until IS NOT NULL"
          + " AND locked_until <= now() THEN 1 ELSE failed_attempts + 1 END) >= ?"
          + " THEN now() + ?::interval ELSE NULL END"
          + " WHERE id = ?"
          + " RETURNING failed_attempts, locked_until IS NOT NULL AS locked";

  /** Creates the repository. */
  public JdbcProtoUserRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public Optional<LoginRow> lockForLogin(String username) {
    return queryOptional(
        "SELECT id, username, full_name, role, active, password_hash,"
            + " (locked_until IS NOT NULL AND locked_until > now()) AS locked_now"
            + " FROM app_user WHERE username = ? FOR UPDATE",
        ps -> ps.setString(1, username),
        rs ->
            new LoginRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("active"),
                rs.getString("password_hash"),
                rs.getBoolean("locked_now")));
  }

  @Override
  public boolean recordFailure(long userId, int threshold, Duration lockout) {
    return queryOne(
        RECORD_FAILURE,
        ps -> {
          ps.setInt(1, threshold);
          ps.setString(2, lockout.toSeconds() + " seconds");
          ps.setLong(3, userId);
        },
        rs -> rs.getBoolean("locked"));
  }

  @Override
  public void resetFailures(long userId) {
    update(
        "UPDATE app_user SET failed_attempts = 0, locked_until = NULL"
            + " WHERE id = ? AND (failed_attempts <> 0 OR locked_until IS NOT NULL)",
        ps -> ps.setLong(1, userId));
  }

  @Override
  public long insert(String username, String passwordHash, Role role, String fullName) {
    return queryOne(
        "INSERT INTO app_user (username, password_hash, role, full_name) VALUES (?, ?, ?, ?)"
            + " RETURNING id",
        ps -> {
          ps.setString(1, username);
          ps.setString(2, passwordHash);
          ps.setString(3, role.name());
          ps.setString(4, fullName);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public List<UserRow> list(int limit) {
    return queryList(
        "SELECT id, username, full_name, role, active FROM app_user ORDER BY username LIMIT ?",
        ps -> ps.setInt(1, limit),
        rs ->
            new UserRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("active")),
        limit);
  }

  @Override
  public boolean any() {
    return queryOne(
        "SELECT EXISTS (SELECT 1 FROM app_user)", StatementBinder.NONE, rs -> rs.getBoolean(1));
  }
}
