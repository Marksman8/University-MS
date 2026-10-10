package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.RuleViolationException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.testkit.TestDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * With {@code logServerErrorDetail=false} the driver no longer puts row values into exception
 * messages, yet {@link SqlErrorMapper} still extracts the constraint name.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverErrorDetailIT {

  private static final String SECRET_VALUE = "alice-private-value";
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final SqlErrorMapper errors =
      new SqlErrorMapper(
          new ConstraintErrorMap(Map.of("uq_it_probe_code", ErrorCode.USERNAME_TAKEN)));

  private DataSources pools;
  private TransactionRunner tx;
  private ProbeRepository probes;

  @BeforeAll
  void start() throws SQLException {
    TestDatabase.exec("CREATE SCHEMA IF NOT EXISTS ums", ProbeRepository.CREATE_TABLE);
    pools = DataSources.create(TestDatabase.config(Duration.ofSeconds(10)), errors);
    tx = new TransactionRunner(pools.app(), errors, new RetryPolicy(new Random(), Sleeper.SYSTEM));
    probes = new ProbeRepository(tx, errors);
  }

  @BeforeEach
  void clean() throws SQLException {
    TestDatabase.exec("TRUNCATE ums.it_probe RESTART IDENTITY");
  }

  @AfterAll
  void stop() throws SQLException {
    pools.close();
    TestDatabase.exec("DROP TABLE IF EXISTS ums.it_probe");
  }

  @Test
  void appPoolHidesRowValuesButConstraintIsStillMapped() {
    tx.inTransaction(false, () -> probes.insert(SECRET_VALUE, 1, NOW));

    assertThatThrownBy(() -> tx.inTransaction(false, () -> probes.insert(SECRET_VALUE, 2, NOW)))
        .isInstanceOfSatisfying(
            RuleViolationException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.USERNAME_TAKEN);
              assertThat(fullText(e)).doesNotContain(SECRET_VALUE).contains("uq_it_probe_code");
            });
  }

  @Test
  void listenerConnectionHidesRowValuesButConstraintIsStillMapped() throws SQLException {
    TestDatabase.exec(
        "INSERT INTO ums.it_probe (code, bucket, created_at) VALUES ('"
            + SECRET_VALUE
            + "', 1, now())");
    ListenerConnectionFactory factory =
        new ListenerConnectionFactory(TestDatabase.config(Duration.ofSeconds(10)), errors);
    try (Connection connection = factory.open();
        Statement st = connection.createStatement()) {
      SQLException raw =
          org.junit.jupiter.api.Assertions.assertThrows(
              SQLException.class,
              () ->
                  st.execute(
                      "INSERT INTO it_probe (code, bucket, created_at) VALUES ('"
                          + SECRET_VALUE
                          + "', 2, now())"));
      assertThat(fullText(raw)).doesNotContain(SECRET_VALUE);
      assertThat(errors.map(raw, false).code()).isEqualTo(ErrorCode.USERNAME_TAKEN);
    }
  }

  @Test
  void controlDefaultDriverSettingDoesExposeRowValues() throws SQLException {
    TestDatabase.exec(
        "INSERT INTO ums.it_probe (code, bucket, created_at) VALUES ('"
            + SECRET_VALUE
            + "', 1, now())");
    try (Connection connection = TestDatabase.admin(false);
        Statement st = connection.createStatement()) {
      SQLException raw =
          org.junit.jupiter.api.Assertions.assertThrows(
              SQLException.class,
              () ->
                  st.execute(
                      "INSERT INTO ums.it_probe (code, bucket, created_at) VALUES ('"
                          + SECRET_VALUE
                          + "', 2, now())"));
      assertThat(fullText(raw)).contains(SECRET_VALUE);
    }
  }

  private static String fullText(Throwable t) {
    StringBuilder text = new StringBuilder();
    for (Throwable c = t; c != null; c = c.getCause()) {
      text.append(c).append('\n');
      if (c instanceof SQLException sql && sql.getNextException() != null) {
        text.append(sql.getNextException()).append('\n');
      }
    }
    return text.toString();
  }
}
