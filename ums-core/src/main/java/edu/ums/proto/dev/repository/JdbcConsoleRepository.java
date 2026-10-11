package edu.ums.proto.dev.repository;

import edu.ums.api.proto.QueryResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * The single place that executes user SQL: on the console pool ({@code ums_dev_console}, read-only
 * role), read-only connection, {@code maxRows 201}, 5 s query timeout.
 */
// Complexity: time O(r × c) per query, r <= 201 rows, c columns; space O(r × c), cells <= 1,000
// characters each
public final class JdbcConsoleRepository implements ConsoleRepository {

  private static final int TIMEOUT_SECONDS = 5;
  private static final int MAX_CELL = 1_000;

  private final DataSource console;

  /** Creates the repository on the console pool. */
  public JdbcConsoleRepository(DataSource console) {
    this.console = Objects.requireNonNull(console, "console");
  }

  @Override
  public QueryResult run(String sql) {
    long started = System.nanoTime();
    try (Connection c = console.getConnection()) {
      c.setAutoCommit(false);
      c.setReadOnly(true);
      try (Statement st = c.createStatement()) {
        st.setMaxRows(MAX_ROWS + 1);
        st.setQueryTimeout(TIMEOUT_SECONDS);
        try (ResultSet rs = st.executeQuery(sql)) {
          return read(rs, started);
        }
      } finally {
        c.rollback();
      }
    } catch (SQLException e) {
      return new QueryResult(
          List.of(),
          List.of(),
          false,
          elapsed(started),
          e.getSQLState() == null ? "" : e.getSQLState(),
          bounded(String.valueOf(e.getMessage())));
    }
  }

  private static QueryResult read(ResultSet rs, long started) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columns = meta.getColumnCount();
    List<String> names = new ArrayList<>(columns);
    for (int i = 1; i <= columns; i++) {
      names.add(meta.getColumnLabel(i));
    }
    List<List<String>> rows = new ArrayList<>();
    boolean truncated = false;
    while (rs.next()) {
      if (rows.size() == MAX_ROWS) {
        truncated = true;
        break;
      }
      List<String> row = new ArrayList<>(columns);
      for (int i = 1; i <= columns; i++) {
        String value = rs.getString(i);
        row.add(value == null ? "NULL" : bounded(value));
      }
      rows.add(List.copyOf(row));
    }
    return new QueryResult(
        List.copyOf(names), List.copyOf(rows), truncated, elapsed(started), null, null);
  }

  private static String bounded(String value) {
    return value.length() <= MAX_CELL ? value : value.substring(0, MAX_CELL) + "…";
  }

  private static long elapsed(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }
}
