package edu.ums.platform.db;

import edu.ums.platform.observe.HealthProbe;
import edu.ums.platform.observe.HealthStatus;
import edu.ums.platform.observe.ProbeResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;

/** {@code SELECT 1} on the application pool with a 2 s query timeout. */
// Complexity: time O(1) per check (one round trip, bounded by the timeout); space O(1)
public final class DatabaseReachableProbe implements HealthProbe {

  /** Probe name. */
  public static final String NAME = "database";

  private static final int QUERY_TIMEOUT_SECONDS = 2;

  private final DataSource dataSource;

  /** Creates the probe over the application pool. */
  public DatabaseReachableProbe(DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ProbeResult check() {
    long start = System.nanoTime();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.execute("SELECT 1");
      long millis = (System.nanoTime() - start) / 1_000_000;
      return new ProbeResult(NAME, HealthStatus.UP, "SELECT 1 in " + millis + " ms");
    } catch (SQLException e) {
      return new ProbeResult(
          NAME, HealthStatus.DOWN, "unreachable (sqlState=" + e.getSQLState() + ")");
    }
  }
}
