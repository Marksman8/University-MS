package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;
import java.util.Objects;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Translates a JDBC failure into the {@link UmsException} hierarchy (master prompt §10). The
 * original exception is always kept as the cause; {@code details} stay empty so no SQL text, value
 * or constraint detail can leak to the user.
 */
// Complexity: time O(d) per map, d = exception chain depth (capped at 16), plus O(1) constraint
// lookup; space O(1)
public final class SqlErrorMapper {

  private static final int MAX_CHAIN_DEPTH = 16;

  private final ConstraintErrorMap constraints;

  /** Creates a mapper using {@code constraints} for integrity violations. */
  public SqlErrorMapper(ConstraintErrorMap constraints) {
    this.constraints = Objects.requireNonNull(constraints, "constraints");
  }

  /**
   * Maps {@code e}.
   *
   * @param cancelRequested whether the caller cancelled the statement; decides {@code CANCELLED} vs
   *     {@code TIMEOUT} for SQLState 57014
   * @return never {@code null}
   */
  public UmsException map(SQLException e, boolean cancelRequested) {
    Objects.requireNonNull(e, "e");
    String state = findSqlState(e);
    if (e instanceof SQLTransientConnectionException
        && (state == null || !state.startsWith("08"))) {
      return new BusyException(e); // HikariCP pool wait timed out
    }
    if (state == null) {
      return internal(e);
    }
    if (state.startsWith("08")) {
      return new DataUnavailableException(e);
    }
    return switch (state) {
      case "40001", "40P01" -> new ConflictException(e);
      case "23505", "23P01", "23514" -> fromConstraint(e);
      case "23503" -> new NotFoundException(Map.of(), e);
      case "55P03" -> new BusyException(e);
      case "57014" ->
          new InfrastructureException(cancelRequested ? ErrorCode.CANCELLED : ErrorCode.TIMEOUT, e);
      default -> internal(e);
    };
  }

  private UmsException fromConstraint(SQLException e) {
    return constraints
        .lookup(findConstraint(e))
        .map(code -> forConstraintCode(code, e))
        .orElseGet(() -> internal(e));
  }

  private static UmsException forConstraintCode(ErrorCode code, SQLException e) {
    return switch (code) {
      case GUARD_FAILED -> new GuardFailedException(code, Map.of(), e);
      case STALE_DATA -> new StaleDataException(Map.of(), e);
      default -> new RuleViolationException(code, Map.of(), e);
    };
  }

  private static UmsException internal(SQLException e) {
    return new InfrastructureException(ErrorCode.INTERNAL_ERROR, e);
  }

  /** Returns the first non-null SQLState along getNextException()/getCause(). */
  private static String findSqlState(SQLException e) {
    Throwable current = e;
    for (int depth = 0; current != null && depth < MAX_CHAIN_DEPTH; depth++) {
      if (current instanceof SQLException sql && sql.getSQLState() != null) {
        return sql.getSQLState();
      }
      current = next(current);
    }
    return null;
  }

  /** Returns the constraint name reported by the server, or {@code null}. */
  private static String findConstraint(SQLException e) {
    Throwable current = e;
    for (int depth = 0; current != null && depth < MAX_CHAIN_DEPTH; depth++) {
      if (current instanceof PSQLException psql) {
        ServerErrorMessage server = psql.getServerErrorMessage();
        if (server != null && server.getConstraint() != null) {
          return server.getConstraint();
        }
      }
      current = next(current);
    }
    return null;
  }

  private static Throwable next(Throwable current) {
    if (current instanceof SQLException sql && sql.getNextException() != null) {
      return sql.getNextException();
    }
    return current.getCause();
  }
}
