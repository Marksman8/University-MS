package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.UmsException;
import edu.ums.testkit.FailureInjector;
import edu.ums.testkit.TestDatabase;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FailureInjector provokes the infrastructure failures the platform must map correctly. */
class FailureInjectorIT {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final SqlErrorMapper errors = new SqlErrorMapper(ConstraintErrorMap.standard());
  private DataSources pools;
  private TransactionRunner tx;
  private ProbeRepository probes;

  @BeforeEach
  void start() throws SQLException {
    TestDatabase.exec(
        "CREATE SCHEMA IF NOT EXISTS ums",
        ProbeRepository.CREATE_TABLE,
        "TRUNCATE ums.it_probe RESTART IDENTITY");
    pools =
        DataSources.create(
            TestDatabase.config(
                Duration.ofSeconds(10), Duration.ofMillis(500), Duration.ofMillis(250)),
            errors);
    tx = new TransactionRunner(pools.app(), errors, new RetryPolicy(new Random(), Sleeper.SYSTEM));
    probes = new ProbeRepository(tx, errors);
  }

  @AfterEach
  void stop() throws SQLException {
    pools.close();
    TestDatabase.exec("DROP TABLE IF EXISTS ums.it_probe");
  }

  @Test
  void blockedTableMakesWritersBusyUntilReleased() throws SQLException {
    FailureInjector.Held lock = FailureInjector.blockTable("it_probe");
    try {
      assertThatThrownBy(() -> tx.inTransaction(false, () -> probes.insert("x", 1, NOW)))
          .isInstanceOfSatisfying(
              UmsException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSY_TRY_AGAIN));
    } finally {
      lock.close();
    }
    tx.inTransaction(false, () -> probes.insert("x", 1, NOW));
    assertThat(tx.inTransaction(true, probes::count)).isEqualTo(1);
  }

  @Test
  void exhaustedPoolMakesBorrowersBusyUntilReleased() throws SQLException {
    FailureInjector.Held held = FailureInjector.exhaustPool((HikariDataSource) pools.app());
    try {
      assertThatThrownBy(() -> tx.inTransaction(true, probes::count))
          .isInstanceOfSatisfying(
              UmsException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSY_TRY_AGAIN));
    } finally {
      held.close();
    }
    assertThat(tx.inTransaction(true, probes::count)).isZero();
  }

  @Test
  void terminatingAppConnectionsKillsPoolBackends() throws SQLException {
    tx.inTransaction(true, probes::count);

    assertThat(FailureInjector.terminateAppConnections()).isPositive();
  }
}
