package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.UmsException;
import edu.ums.platform.error.ValidationException;
import edu.ums.testkit.TestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
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

/** Transactions, pool session settings and timeouts against a real PostgreSQL 18. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionIT {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private DataSources pools;
  private TransactionRunner tx;
  private ProbeRepository probes;

  @BeforeAll
  void start() throws SQLException {
    TestDatabase.exec("CREATE SCHEMA IF NOT EXISTS ums", ProbeRepository.CREATE_TABLE);
    SqlErrorMapper errors = new SqlErrorMapper(ConstraintErrorMap.standard());
    pools = DataSources.create(TestDatabase.config(Duration.ofSeconds(1)), errors);
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
  void commitMakesChangesVisible() {
    tx.inTransaction(false, () -> probes.insert("a", 1, NOW));
    assertThat(tx.inTransaction(true, probes::count)).isEqualTo(1);
  }

  @Test
  void exceptionRollsBackAndUnbindsThread() {
    assertThatThrownBy(
            () ->
                tx.inTransaction(
                    false,
                    () -> {
                      probes.insert("a", 1, NOW);
                      throw new ValidationException(Map.of());
                    }))
        .isInstanceOf(ValidationException.class);

    assertThat(tx.isActive()).isFalse();
    assertThat(tx.inTransaction(true, probes::count)).isZero();
  }

  @Test
  void nestedTransactionJoinsOuterAndRollsBackWithIt() {
    assertThatThrownBy(
            () ->
                tx.inTransaction(
                    false,
                    () -> {
                      Connection outer = tx.current().connection();
                      probes.insert("outer", 1, NOW);
                      tx.inTransaction(
                          false,
                          () -> {
                            assertThat(tx.current().connection()).isSameAs(outer);
                            return probes.insert("inner", 1, NOW);
                          });
                      assertThat(probes.count()).isEqualTo(2);
                      throw new ValidationException(Map.of());
                    }))
        .isInstanceOf(ValidationException.class);

    assertThat(tx.inTransaction(true, probes::count)).isZero();
  }

  @Test
  void readOnlyTransactionRejectsWrites() {
    assertThatThrownBy(() -> tx.inTransaction(true, () -> probes.insert("a", 1, NOW)))
        .isInstanceOf(UmsException.class)
        .hasRootCauseInstanceOf(SQLException.class)
        .rootCause()
        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("25006"));

    assertThat(tx.inTransaction(true, probes::count)).isZero();
  }

  @Test
  void statementTimeoutMapsToTimeout() {
    assertThatThrownBy(() -> tx.inTransaction(true, () -> probes.sleep(3)))
        .isInstanceOfSatisfying(
            UmsException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TIMEOUT));
    assertThat(tx.isActive()).isFalse();
  }

  @Test
  void poolsApplySchemaTimeoutsAndNames() throws SQLException {
    assertThat(setting(pools.app(), "search_path")).isEqualTo("ums");
    assertThat(setting(pools.app(), "statement_timeout")).isEqualTo("1s");
    assertThat(setting(pools.app(), "lock_timeout")).isEqualTo("5s");
    assertThat(setting(pools.console(), "statement_timeout")).isEqualTo("15s");
    assertThat(setting(pools.app(), "application_name")).isEqualTo(DataSources.APP_POOL);
    assertThat(pools.poolNames()).containsExactly(DataSources.APP_POOL, DataSources.CONSOLE_POOL);
  }

  @Test
  void listenerConnectionUsesSchemaAndAutoCommit() throws SQLException {
    SqlErrorMapper errors = new SqlErrorMapper(ConstraintErrorMap.standard());
    ListenerConnectionFactory factory =
        new ListenerConnectionFactory(TestDatabase.config(Duration.ofSeconds(1)), errors);
    try (Connection connection = factory.open();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SHOW search_path")) {
      rs.next();
      assertThat(rs.getString(1)).isEqualTo("ums");
      assertThat(connection.getAutoCommit()).isTrue();
    }
  }

  private static String setting(javax.sql.DataSource ds, String name) throws SQLException {
    try (Connection connection = ds.getConnection();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SHOW " + name)) {
      rs.next();
      return rs.getString(1);
    }
  }
}
