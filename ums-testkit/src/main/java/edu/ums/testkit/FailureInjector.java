package edu.ums.testkit;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Provokes infrastructure failures in integration tests. */
public final class FailureInjector {

  /** Locks the table named by the transaction-local setting; %I quotes the identifier. */
  private static final String LOCK_NAMED_TABLE =
      "DO $$ BEGIN EXECUTE format('LOCK TABLE ums.%I IN ACCESS EXCLUSIVE MODE',"
          + " current_setting('ums.it_lock_table')); END $$";

  private FailureInjector() {}

  /**
   * Terminates every backend opened by the UMS pools ({@code application_name} {@code ums-app} or
   * {@code ums-console}) in the test database.
   *
   * @return the number of backends terminated
   */
  public static int terminateAppConnections() throws SQLException {
    String sql =
        "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity"
            + " WHERE datname = current_database() AND pid <> pg_backend_pid()"
            + " AND application_name IN ('ums-app', 'ums-console')";
    try (Connection admin = TestDatabase.admin();
        Statement st = admin.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  /**
   * Borrows every connection of {@code pool} and holds them until the returned handle is closed.
   * While held, further borrows wait for the pool's connection timeout and then fail.
   */
  public static Held exhaustPool(HikariDataSource pool) throws SQLException {
    List<Connection> borrowed = new ArrayList<>(pool.getMaximumPoolSize());
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) {
        borrowed.add(pool.getConnection());
      }
    } catch (SQLException e) {
      closeAll(borrowed);
      throw e;
    }
    return () -> closeAll(borrowed);
  }

  /**
   * Takes an ACCESS EXCLUSIVE lock on {@code ums.<table>} in a side connection and holds it until
   * the returned handle is closed (the side transaction is then rolled back).
   */
  public static Held blockTable(String table) throws SQLException {
    Connection side = TestDatabase.admin();
    try {
      side.setAutoCommit(false);
      try (PreparedStatement ps =
          side.prepareStatement("SELECT set_config('ums.it_lock_table', ?, true)")) {
        ps.setString(1, table);
        ps.execute();
      }
      try (Statement st = side.createStatement()) {
        st.execute(LOCK_NAMED_TABLE);
      }
    } catch (SQLException e) {
      side.close();
      throw e;
    }
    return () -> {
      side.rollback();
      side.close();
    };
  }

  private static void closeAll(List<Connection> connections) throws SQLException {
    SQLException first = null;
    for (Connection connection : connections) {
      try {
        connection.close();
      } catch (SQLException e) {
        if (first == null) {
          first = e;
        } else {
          first.addSuppressed(e);
        }
      }
    }
    if (first != null) {
      throw first;
    }
  }

  /** A held failure condition; closing releases it. */
  @FunctionalInterface
  public interface Held extends AutoCloseable {
    @Override
    void close() throws SQLException;
  }
}
