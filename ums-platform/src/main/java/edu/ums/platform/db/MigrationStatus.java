package edu.ums.platform.db;

/**
 * Schema state relative to the migrations this application ships.
 *
 * @param pending migrations on the classpath not yet applied (schema behind the code)
 * @param future migrations applied to the database that this build does not know (schema ahead of
 *     the code, e.g. after rolling the application back one version)
 */
public record MigrationStatus(int pending, int future) {

  /** Returns {@code true} when the database has migrations newer than this application. */
  public boolean isAhead() {
    return future > 0;
  }
}
