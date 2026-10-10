package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.StaleDataException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** BaseRepository template methods against a real PostgreSQL 18. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryTemplatesIT {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private DataSources pools;
  private TransactionRunner tx;
  private ProbeRepository probes;

  @BeforeAll
  void start() throws SQLException {
    DbTestSupport.exec("CREATE SCHEMA IF NOT EXISTS ums", ProbeRepository.CREATE_TABLE);
    SqlErrorMapper errors = new SqlErrorMapper(ConstraintErrorMap.standard());
    pools = DataSources.create(DbTestSupport.config(Duration.ofSeconds(10)), errors);
    tx = new TransactionRunner(pools.app(), errors, new RetryPolicy(new Random(), Sleeper.SYSTEM));
    probes = new ProbeRepository(tx, errors);
  }

  @BeforeEach
  void clean() throws SQLException {
    DbTestSupport.exec("TRUNCATE ums.it_probe RESTART IDENTITY");
  }

  @AfterAll
  void stop() throws SQLException {
    pools.close();
    DbTestSupport.exec("DROP TABLE IF EXISTS ums.it_probe");
  }

  @Test
  void queryOneMissingRowIsNotFound() {
    assertThatThrownBy(() -> tx.inTransaction(true, () -> probes.get(999)))
        .isInstanceOf(NotFoundException.class);
    assertThat(tx.inTransaction(true, () -> probes.find("nope"))).isEmpty();
  }

  @Test
  void casStateAdvancesVersionAndRejectsStaleVersionOrState() {
    long id = tx.inTransaction(false, () -> probes.insert("a", 1, NOW));

    long v1 = tx.inTransaction(false, () -> probes.transition(id, "DRAFT", "SUBMITTED", 0));
    assertThat(v1).isEqualTo(1);
    assertThat(tx.inTransaction(true, () -> probes.get(id)))
        .extracting(ProbeRepository.Probe::state, ProbeRepository.Probe::version)
        .containsExactly("SUBMITTED", 1L);

    assertThatThrownBy(
            () -> tx.inTransaction(false, () -> probes.transition(id, "SUBMITTED", "APPROVED", 0)))
        .isInstanceOfSatisfying(
            StaleDataException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.STALE_DATA));
    assertThatThrownBy(
            () -> tx.inTransaction(false, () -> probes.transition(id, "DRAFT", "APPROVED", 1)))
        .isInstanceOf(StaleDataException.class);
  }

  @Test
  void batchInsertsAllItemsInOneTransaction() {
    int rows = tx.inTransaction(false, () -> probes.insertAll(List.of("x", "y", "z"), NOW));
    assertThat(rows).isEqualTo(3);
    assertThat(tx.inTransaction(true, probes::count)).isEqualTo(3);
  }

  @Test
  void batchFailureRollsBackEveryItem() {
    assertThatThrownBy(
            () -> tx.inTransaction(false, () -> probes.insertAll(List.of("dup", "dup"), NOW)))
        .isInstanceOfSatisfying(
            InfrastructureException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    assertThat(tx.inTransaction(true, probes::count)).isZero();
  }

  @Test
  void repositoryOutsideTransactionIsInternalError() {
    assertThatThrownBy(probes::count)
        .isInstanceOfSatisfying(
            InfrastructureException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
  }
}
