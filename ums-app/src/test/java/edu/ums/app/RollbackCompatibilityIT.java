package edu.ums.app;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.ClasspathConfigSource;
import edu.ums.platform.config.ConfigLoader;
import edu.ums.platform.db.MigrationRunner;
import edu.ums.platform.db.MigrationsCurrentProbe;
import edu.ums.platform.observe.HealthReport;
import edu.ums.platform.observe.HealthStatus;
import edu.ums.platform.observe.ProbeResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * SDLC rollback plan: version N-1 of the application must start on version N's schema. The database
 * gets V1, V2 and a test-only V3; the runtime under test ships only V1-V2.
 */
class RollbackCompatibilityIT {

  private final ClasspathConfigSource source =
      new ClasspathConfigSource(RollbackCompatibilityIT.class.getClassLoader());

  @Test
  void previousApplicationVersionStartsOnANewerSchema() {
    Map<String, String> env = environment();
    AppConfig config = new ConfigLoader(source, env).load();

    // (1) clean DB migrated to "version N": V1, V2 and the test-only V3
    MigrationRunner.forOwner(config).clean();
    Flyway versionN = flyway(config, MigrationRunner.LOCATION, "classpath:db/rollback-test");
    assertThat(versionN.migrate().migrationsExecuted).isEqualTo(3);
    List<String> historyBefore = history(versionN);

    // (2) the N-1 runtime (only V1-V2 on its classpath location)
    UmsRuntime runtime = new UmsRuntime(source, env);
    try {
      runtime.start();

      // (3) READY, UP with the rollback-mode detail, nothing re-applied or failed
      assertThat(runtime.isReady()).isTrue();
      HealthReport health = runtime.health();
      assertThat(health.status()).isEqualTo(HealthStatus.UP);
      assertThat(health.probes())
          .filteredOn(p -> p.name().equals(MigrationsCurrentProbe.NAME))
          .extracting(ProbeResult::status, ProbeResult::detail)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple(
                  HealthStatus.UP, MigrationsCurrentProbe.ROLLBACK_MODE));
      assertThat(MigrationRunner.forOwner(config).status().future()).isEqualTo(1);
    } finally {
      runtime.stop();
    }
    assertThat(history(versionN)).isEqualTo(historyBefore);
    assertThat(historyBefore).containsExactly("1:true", "2:true", "3:true");
  }

  private static Flyway flyway(AppConfig config, String... locations) {
    PGSimpleDataSource ds = new PGSimpleDataSource();
    ds.setUrl(config.database().url());
    ds.setUser(config.database().owner().user());
    ds.setPassword(new String(config.database().owner().password().reveal()));
    return Flyway.configure()
        .dataSource(ds)
        .locations(locations)
        .schemas("ums")
        .defaultSchema("ums")
        .createSchemas(false)
        .load();
  }

  /** Applied migrations as "version:success", in install order. */
  private static List<String> history(Flyway flyway) {
    return java.util.Arrays.stream(flyway.info().applied())
        .map(m -> m.getVersion() + ":" + m.getState().isApplied())
        .toList();
  }

  private static Map<String, String> environment() {
    Map<String, String> env = new HashMap<>(System.getenv());
    String url = System.getenv("UMS_TEST_DB_URL");
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "UMS_TEST_DB_URL must be set to run integration tests (README: Running integration tests locally)");
    }
    env.put("UMS_DB_URL", url);
    env.put("UMS_ENV", "TEST");
    return env;
  }
}
