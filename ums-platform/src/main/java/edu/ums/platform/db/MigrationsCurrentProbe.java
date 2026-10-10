package edu.ums.platform.db;

import edu.ums.platform.error.UmsException;
import edu.ums.platform.observe.HealthProbe;
import edu.ums.platform.observe.HealthStatus;
import edu.ums.platform.observe.ProbeResult;
import java.util.Objects;

/**
 * UP when no migration is pending; DEGRADED when the schema is behind the code (the database is
 * reachable but may lack objects the code needs); DOWN when the state cannot be read.
 */
// Complexity: time O(m) per check, m migrations known to Flyway; space O(m)
public final class MigrationsCurrentProbe implements HealthProbe {

  /** Probe name. */
  public static final String NAME = "migrations";

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
      int pending = migrations.pendingCount();
      return pending == 0
          ? new ProbeResult(NAME, HealthStatus.UP, "schema current")
          : new ProbeResult(NAME, HealthStatus.DEGRADED, pending + " migration(s) pending");
    } catch (UmsException e) {
      return new ProbeResult(
          NAME, HealthStatus.DOWN, "cannot read migration state (" + e.code() + ")");
    }
  }
}
