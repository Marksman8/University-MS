package edu.ums.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import edu.ums.platform.config.ClasspathConfigSource;
import edu.ums.platform.config.ConfigException;
import edu.ums.platform.config.ConfigLoader;
import edu.ums.platform.db.MigrationRunner;
import edu.ums.platform.error.DataUnavailableException;
import edu.ums.platform.observe.HealthReport;
import edu.ums.platform.observe.HealthStatus;
import edu.ums.platform.observe.ProbeResult;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runtime lifecycle against PostgreSQL 18, logging in as the real UMS roles. Needs
 * UMS_TEST_DB_URL/USER/PASSWORD plus the five role passwords (README "Running integration tests
 * locally"); fails loudly when they are missing.
 */
class RuntimeIT {

  /** application_name values set by every UMS connection (pools, listener, migrations). */
  private static final String[] UMS_APPLICATIONS = {
    "ums-app", "ums-console", "ums-listener", "ums-migrations"
  };

  private final ClasspathConfigSource source =
      new ClasspathConfigSource(RuntimeIT.class.getClassLoader());

  @BeforeEach
  void blankSchema() {
    MigrationRunner.forOwner(new ConfigLoader(source, environment()).load()).clean();
  }

  @Test
  void startsOnABlankDatabaseReadyAndUp() throws SQLException {
    UmsRuntime runtime = new UmsRuntime(source, environment());
    try {
      runtime.start();

      assertThat(runtime.isReady()).isTrue();
      HealthReport health = runtime.health();
      assertThat(health.status()).isEqualTo(HealthStatus.UP);
      assertThat(health.probes())
          .extracting(ProbeResult::name, ProbeResult::status)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple("database", HealthStatus.UP),
              org.assertj.core.groups.Tuple.tuple("migrations", HealthStatus.UP));
      assertThat(adminCount("SELECT count(*) FROM ums.flyway_schema_history WHERE success"))
          .isEqualTo(2);
    } finally {
      runtime.stop();
    }
    assertThat(runtime.isReady()).isFalse();
  }

  @Test
  void stopFollowsTheShutdownOrderAndReleasesEveryConnection() throws Exception {
    long baseline = umsConnections();
    UmsRuntime runtime = new UmsRuntime(source, environment());
    runtime.start();
    runtime.health();
    assertThat(umsConnections()).isGreaterThan(baseline);

    List<String> sequence = runtime.stop();

    assertThat(sequence)
        .containsExactly(
            "ACCEPT_STOP:readiness",
            "SCHEDULER:scheduler",
            "POOLS:health-probe",
            "POOLS:connection-pools");
    assertThat(awaitConnections(baseline, Duration.ofSeconds(5))).isEqualTo(baseline);
  }

  @Test
  void startAndStopAreIdempotent() {
    UmsRuntime runtime = new UmsRuntime(source, environment());
    runtime.start();
    runtime.start();
    assertThat(runtime.isReady()).isTrue();
    assertThat(runtime.stop()).isNotEmpty();
    assertThat(runtime.stop()).isEmpty();
    assertThat(runtime.isReady()).isFalse();
    assertThatThrownBy(runtime::health).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void databaseDownAtStartFailsFastWithoutHangingOrLeaking() throws Exception {
    long baseline = umsConnections();
    Map<String, String> env = environment();
    env.put("UMS_DB_URL", "jdbc:postgresql://127.0.0.1:1/ums");
    UmsRuntime runtime = new UmsRuntime(source, env);

    long start = System.nanoTime();
    assertTimeoutPreemptively(
        Duration.ofSeconds(10),
        () -> assertThatThrownBy(runtime::start).isInstanceOf(DataUnavailableException.class));
    long millis = (System.nanoTime() - start) / 1_000_000;

    assertThat(millis).as("startup failure time in ms").isLessThan(10_000);
    assertThat(runtime.isReady()).isFalse();
    assertThat(umsConnections()).isEqualTo(baseline);
    System.getLogger(RuntimeIT.class.getName())
        .log(System.Logger.Level.INFO, "DB-down start failed after " + millis + " ms");
  }

  @Test
  void invalidConfigurationFailsFastListingTheProblem() {
    Map<String, String> env = environment();
    env.remove("UMS_OWNER_DB_PASSWORD");
    UmsRuntime runtime = new UmsRuntime(source, env);

    assertThatThrownBy(runtime::start)
        .isInstanceOfSatisfying(
            ConfigException.class,
            e ->
                assertThat(e.problems())
                    .contains("UMS_OWNER_DB_PASSWORD: required (environment variable)"));
    assertThat(runtime.isReady()).isFalse();
  }

  // --- helpers ---

  private static Map<String, String> environment() {
    Map<String, String> env = new HashMap<>(System.getenv());
    env.put("UMS_DB_URL", required("UMS_TEST_DB_URL"));
    env.put("UMS_ENV", "TEST");
    for (String secret :
        List.of(
            "UMS_DB_PASSWORD",
            "UMS_CONSOLE_DB_PASSWORD",
            "UMS_LISTENER_DB_PASSWORD",
            "UMS_OWNER_DB_PASSWORD",
            "UMS_BACKUP_DB_PASSWORD")) {
      required(secret);
    }
    return env;
  }

  private static long umsConnections() throws SQLException {
    try (Connection c = admin();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM pg_stat_activity"
                    + " WHERE datname = current_database() AND application_name = ANY (?)")) {
      Array names = c.createArrayOf("text", UMS_APPLICATIONS);
      ps.setArray(1, names);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static long awaitConnections(long expected, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    long current = umsConnections();
    while (current != expected && System.nanoTime() < deadline) {
      Thread.sleep(50); // test-only polling: backend processes exit shortly after socket close
      current = umsConnections();
    }
    return current;
  }

  private static long adminCount(String sql) throws SQLException {
    try (Connection c = admin();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static Connection admin() throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("user", required("UMS_TEST_DB_USER"));
    String password = System.getenv("UMS_TEST_DB_PASSWORD");
    properties.setProperty("password", password == null ? "" : password);
    properties.setProperty("ApplicationName", "ums-runtime-it");
    return DriverManager.getConnection(required("UMS_TEST_DB_URL"), properties);
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          name
              + " must be set to run integration tests (README: Running integration tests locally)");
    }
    return value;
  }
}
