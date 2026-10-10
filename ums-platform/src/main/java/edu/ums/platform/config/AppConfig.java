package edu.ums.platform.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable, validated application configuration. Built only by {@link ConfigLoader}. Secrets are
 * {@link Secret}s, so {@code toString()} of any component never reveals them.
 *
 * @param environment deployment environment
 * @param database JDBC URL and the five database accounts
 * @param pools pool sizes and timeouts
 * @param policy business-rule defaults
 * @param paths backup and log directories
 */
public record AppConfig(
    Environment environment,
    DatabaseConfig database,
    PoolConfig pools,
    PolicyDefaults policy,
    PathsConfig paths) {

  /** Rejects {@code null} components. */
  public AppConfig {
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(pools, "pools");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(paths, "paths");
  }

  /** A database login. */
  public record DbAccount(String user, Secret password) {}

  /** JDBC URL plus the app, console, listener, owner and backup accounts. */
  public record DatabaseConfig(
      String url,
      DbAccount app,
      DbAccount console,
      DbAccount listener,
      DbAccount owner,
      DbAccount backup) {}

  /** Pool sizes and per-connection timeouts. */
  public record PoolConfig(
      int appMaxSize,
      int consoleMaxSize,
      Duration connectionTimeout,
      Duration statementTimeout,
      Duration lockTimeout,
      Duration consoleStatementTimeout) {}

  /** Configurable business-rule defaults (master prompt §1). */
  public record PolicyDefaults(
      int creditLimit,
      int attendanceThresholdPercent,
      Duration attendanceEditWindow,
      int lockoutMaxFailures,
      Duration lockoutDuration,
      Duration sessionIdleTimeout) {}

  /** Filesystem locations. */
  public record PathsConfig(Path backupDir, Path backupSecondDir, Path logDir) {}
}
