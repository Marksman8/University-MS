package edu.ums.platform.db;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.SqlErrorMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs work in a READ COMMITTED transaction bound to the current thread (propagation REQUIRED).
 *
 * <ul>
 *   <li>No active transaction: borrow a connection, run, commit; roll back if anything is thrown;
 *       retry the whole transaction on conflict via {@link RetryPolicy}.
 *   <li>Active transaction: join it (no new connection, no retry). Asking for a read-write
 *       transaction inside a read-only one is a programming error.
 * </ul>
 *
 * The thread binding is always removed when the outermost transaction ends.
 */
// Complexity: time O(1) overhead per call (one ThreadLocal lookup); space O(1) per thread
public final class TransactionRunner {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionRunner.class);

  private final DataSource dataSource;
  private final SqlErrorMapper errors;
  private final RetryPolicy retryPolicy;
  private final ThreadLocal<Tx> current = new ThreadLocal<>();

  /** Creates a runner over {@code dataSource}. */
  public TransactionRunner(DataSource dataSource, SqlErrorMapper errors, RetryPolicy retryPolicy) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    this.errors = Objects.requireNonNull(errors, "errors");
    this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
  }

  /**
   * Runs {@code work} in a transaction.
   *
   * @param readOnly open the transaction read-only (ignored when joining)
   * @return the work's result
   */
  public <T> T inTransaction(boolean readOnly, Supplier<T> work) {
    Objects.requireNonNull(work, "work");
    Tx outer = current.get();
    if (outer != null) {
      if (outer.readOnly() && !readOnly) {
        throw new InfrastructureException(
            ErrorCode.INTERNAL_ERROR,
            new IllegalStateException("read-write transaction requested inside a read-only one"));
      }
      return work.get();
    }
    return retryPolicy.execute(() -> runNew(readOnly, work));
  }

  /** Returns {@code true} if the current thread is inside a transaction. */
  public boolean isActive() {
    return current.get() != null;
  }

  /**
   * Returns the current thread's transaction.
   *
   * @throws InfrastructureException {@code INTERNAL_ERROR} if there is none
   */
  public Tx current() {
    Tx tx = current.get();
    if (tx == null) {
      throw new InfrastructureException(
          ErrorCode.INTERNAL_ERROR, new IllegalStateException("no active transaction"));
    }
    return tx;
  }

  private <T> T runNew(boolean readOnly, Supplier<T> work) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      connection.setReadOnly(readOnly);
      current.set(new Tx(connection, readOnly));
      boolean committed = false;
      try {
        T result = work.get();
        connection.commit();
        committed = true;
        return result;
      } finally {
        current.remove();
        if (!committed) {
          rollback(connection);
        }
      }
    } catch (SQLException e) {
      throw errors.map(e, false);
    }
  }

  private static void rollback(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException e) {
      LOG.warn(
          "Rollback failed (sqlState={}); the pool resets or evicts the connection on close",
          e.getSQLState());
    }
  }
}
