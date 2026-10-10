package edu.ums.app;

import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.ConfigLoader;
import edu.ums.platform.config.ConfigSource;
import edu.ums.platform.db.DataSources;
import edu.ums.platform.db.MigrationRunner;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.DataUnavailableException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.observe.HealthReport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application lifecycle. {@link #start()}: load config → open pools → migrate → start executors →
 * mark ready. {@link #stop()}: the shutdown sequence of {@link
 * edu.ums.platform.concurrent.ManagedExecutors}. Both are idempotent and thread-safe.
 */
public final class UmsRuntime {

  /** Time budget per shutdown stage. */
  static final Duration STAGE_BUDGET = Duration.ofSeconds(5);

  private static final Logger LOG = LoggerFactory.getLogger(UmsRuntime.class);

  private final ConfigSource source;
  private final Map<String, String> environment;
  private AppContext context;

  /** Creates a runtime reading configuration from {@code source} and {@code environment}. */
  public UmsRuntime(ConfigSource source, Map<String, String> environment) {
    this.source = Objects.requireNonNull(source, "source");
    this.environment = Map.copyOf(environment);
  }

  /**
   * Starts the application. Fails fast (no hang) when configuration is invalid or the database is
   * unreachable; nothing stays open after a failed start.
   *
   * @throws edu.ums.platform.config.ConfigException listing every configuration problem
   * @throws DataUnavailableException if the database cannot be reached
   */
  public synchronized void start() {
    if (context != null) {
      return;
    }
    AppConfig config = new ConfigLoader(source, environment).load();
    LOG.info("UMS starting (environment {})", config.environment());
    SqlErrorMapper sqlErrors = new SqlErrorMapper(ConstraintErrorMap.standard());
    DataSources pools = openPools(config, sqlErrors);
    boolean started = false;
    try {
      MigrationRunner migrations = MigrationRunner.forOwner(config);
      int applied = migrations.migrate();
      AppContext built = new AppContext(config, sqlErrors, pools, migrations);
      built.readiness().markReady();
      context = built;
      started = true;
      LOG.info("UMS ready ({} migration(s) applied)", applied);
    } finally {
      if (!started) {
        pools.close();
      }
    }
  }

  /**
   * Stops the application in the documented order.
   *
   * @return the components stopped, as {@code STAGE:name}; empty if not running
   */
  public synchronized List<String> stop() {
    if (context == null) {
      return List.of();
    }
    LOG.info("UMS stopping");
    List<String> sequence = context.executors().shutdownAll(STAGE_BUDGET);
    context = null;
    LOG.info("UMS stopped: {}", sequence);
    return sequence;
  }

  /** Returns {@code true} while started and serving. */
  public synchronized boolean isReady() {
    return context != null && context.readiness().isReady();
  }

  /**
   * Runs the health probes.
   *
   * @throws IllegalStateException if not started
   */
  public HealthReport health() {
    AppContext current;
    synchronized (this) {
      current = context;
    }
    if (current == null) {
      throw new IllegalStateException("UMS is not running");
    }
    return current.health().check();
  }

  private static DataSources openPools(AppConfig config, SqlErrorMapper sqlErrors) {
    try {
      return DataSources.create(config, sqlErrors);
    } catch (DataUnavailableException e) {
      LOG.error(
          "UMS cannot start: database unavailable. Check that PostgreSQL is running and that"
              + " UMS_DB_URL / ums.db.url points at it.");
      throw e;
    }
  }
}
