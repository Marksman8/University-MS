package edu.ums.app;

import edu.ums.app.proto.ProtoModule;
import edu.ums.platform.concurrent.ManagedExecutors;
import edu.ums.platform.concurrent.RejectionMode;
import edu.ums.platform.concurrent.Scheduler;
import edu.ums.platform.concurrent.ShutdownStage;
import edu.ums.platform.concurrent.UncaughtErrorSink;
import edu.ums.platform.config.AppConfig;
import edu.ums.platform.db.DataSources;
import edu.ums.platform.db.DatabaseReachableProbe;
import edu.ums.platform.db.ListenerConnectionFactory;
import edu.ums.platform.db.MigrationRunner;
import edu.ums.platform.db.MigrationsCurrentProbe;
import edu.ums.platform.db.RetryPolicy;
import edu.ums.platform.db.Sleeper;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.ApiErrorTranslator;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.invoke.FacadeProxyFactory;
import edu.ums.platform.observe.HealthService;
import edu.ums.platform.observe.MetricsRegistry;
import edu.ums.platform.observe.Readiness;
import edu.ums.platform.observe.Tracer;
import edu.ums.proto.auth.service.PasswordHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Composition root: every long-lived object, wired by constructors in one place. Built by {@link
 * UmsRuntime} after the pools are open and migrations have run. Registers each component in the
 * shutdown sequence: readiness off (ACCEPT_STOP) → scheduler → worker pools → connection pools.
 */
public final class AppContext {

  /** Per-probe health timeout (master prompt §12 / P0 Step 11). */
  static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

  private final AppConfig config;
  private final SqlErrorMapper sqlErrors;
  private final ApiErrorTranslator apiErrors;
  private final DataSources dataSources;
  private final TransactionRunner transactions;
  private final MigrationRunner migrations;
  private final ListenerConnectionFactory listenerConnections;
  private final ManagedExecutors executors;
  private final Scheduler scheduler;
  private final HealthService health;
  private final Readiness readiness;
  private final Tracer tracer;
  private final MetricsRegistry metrics;
  private final FacadeProxyFactory facades;
  private final ProtoModule proto;

  AppContext(
      AppConfig config,
      SqlErrorMapper sqlErrors,
      DataSources dataSources,
      MigrationRunner migrations) {
    SecureRandom random = new SecureRandom();
    this.config = config;
    this.sqlErrors = sqlErrors;
    this.apiErrors = new ApiErrorTranslator(random);
    this.dataSources = dataSources;
    this.migrations = migrations;
    this.transactions =
        new TransactionRunner(
            dataSources.app(), sqlErrors, new RetryPolicy(random, Sleeper.SYSTEM));
    this.listenerConnections = new ListenerConnectionFactory(config, sqlErrors);
    this.tracer = new Tracer();
    this.metrics = new MetricsRegistry();
    this.facades = new FacadeProxyFactory();
    this.readiness = new Readiness();
    this.executors = new ManagedExecutors(UncaughtErrorSink.NONE);
    this.executors.register(ShutdownStage.ACCEPT_STOP, "readiness", t -> readiness.markNotReady());
    this.scheduler = executors.scheduler("scheduler", 2);
    ExecutorService probes = executors.fixedPool("health-probe", 2, 16, RejectionMode.REJECT_BUSY);
    this.executors.register(ShutdownStage.POOLS, "connection-pools", t -> dataSources.close());
    this.health =
        new HealthService(
            List.of(
                new DatabaseReachableProbe(dataSources.app()),
                new MigrationsCurrentProbe(migrations)),
            probes,
            PROBE_TIMEOUT,
            Clock.systemUTC());
    this.proto =
        new ProtoModule(
            config,
            dataSources,
            transactions,
            sqlErrors,
            apiErrors,
            facades,
            tracer,
            metrics,
            health,
            PasswordHasher.COST);
  }

  /** The prototype slice (branch prototype/full-slice only). */
  public ProtoModule proto() {
    return proto;
  }

  public AppConfig config() {
    return config;
  }

  public SqlErrorMapper sqlErrors() {
    return sqlErrors;
  }

  public ApiErrorTranslator apiErrors() {
    return apiErrors;
  }

  public DataSources dataSources() {
    return dataSources;
  }

  public TransactionRunner transactions() {
    return transactions;
  }

  public MigrationRunner migrations() {
    return migrations;
  }

  public ListenerConnectionFactory listenerConnections() {
    return listenerConnections;
  }

  public ManagedExecutors executors() {
    return executors;
  }

  public Scheduler scheduler() {
    return scheduler;
  }

  public HealthService health() {
    return health;
  }

  public Readiness readiness() {
    return readiness;
  }

  public Tracer tracer() {
    return tracer;
  }

  public MetricsRegistry metrics() {
    return metrics;
  }

  public FacadeProxyFactory facades() {
    return facades;
  }
}
