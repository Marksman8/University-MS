package edu.ums.platform.db;

import edu.ums.platform.error.UmsException;
import edu.ums.platform.observe.HealthProbe;
import edu.ums.platform.observe.HealthStatus;
import edu.ums.platform.observe.ProbeResult;
import java.util.Objects;

/**
 * UP when no migration is pending; UP with {@value #ROLLBACK_MODE} when the database has migrations
 * newer than this build (version N-1 running on version N's schema); DEGRADED when the schema is
 * behind the code (reachable, but may lack objects the code needs); DOWN when the state cannot be
 * read.
 */
// Complexity: time O(m) per check, m migrations known to Flyway; space O(m)
public final class MigrationsCurrentProbe implements HealthProbe {

  /** Probe name. */
  public static final String NAME = "migrations";

  /** Detail reported when the schema is ahead of the application. */
  public static final String ROLLBACK_MODE = "schema ahead of application (rollback mode)";

  private final MigrationRunner migrations;

  /** Creates the probe. */
  public MigrationsCurrentProbe(MigrationRunner migrations) {
    this.migrations = Objects.requireNonNull(migrations, "migrations");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ProbeResult check() {
    try {
      MigrationStatus status = migrations.status();
      if (status.isAhead()) {
        return new ProbeResult(NAME, HealthStatus.UP, ROLLBACK_MODE);
      }
      return status.pending() == 0
          ? new ProbeResult(NAME, HealthStatus.UP, "schema current")
          : new ProbeResult(
              NAME, HealthStatus.DEGRADED, status.pending() + " migration(s) pending");
    } catch (UmsException e) {
      return new ProbeResult(
          NAME, HealthStatus.DOWN, "cannot read migration state (" + e.code() + ")");
    }
  }
}
