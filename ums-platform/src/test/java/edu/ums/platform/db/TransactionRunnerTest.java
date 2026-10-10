package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.ConflictException;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.ValidationException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Unit tests with a fake DataSource/Connection (no database). */
class TransactionRunnerTest {

  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final AtomicInteger sleeps = new AtomicInteger();
  private final TransactionRunner runner =
      new TransactionRunner(
          dataSource,
          new SqlErrorMapper(ConstraintErrorMap.standard()),
          new RetryPolicy(new Random(5), millis -> sleeps.incrementAndGet()));

  @BeforeEach
  void connect() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
  }

  @Test
  void commitsOnSuccessAtReadCommitted() throws SQLException {
    assertThat(runner.inTransaction(false, () -> "done")).isEqualTo("done");

    InOrder order = inOrder(connection);
    order.verify(connection).setAutoCommit(false);
    order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    order.verify(connection).setReadOnly(false);
    order.verify(connection).commit();
    order.verify(connection).close();
    verify(connection, never()).rollback();
    assertThat(runner.isActive()).isFalse();
  }

  @Test
  void rollsBackOnExceptionAndClearsBinding() throws SQLException {
    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    false,
                    () -> {
                      throw new ValidationException(Map.of());
                    }))
        .isInstanceOf(ValidationException.class);

    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).close();
    assertThat(runner.isActive()).isFalse();
  }

  @Test
  void retriesWholeTransactionOnInjected40001AtCommit() throws SQLException {
    doThrow(new SQLException("could not serialize access", "40001"))
        .doNothing()
        .when(connection)
        .commit();
    AtomicInteger runs = new AtomicInteger();

    runner.inTransaction(false, runs::incrementAndGet);

    assertThat(runs).hasValue(2);
    verify(dataSource, times(2)).getConnection();
    verify(connection, times(1)).rollback();
    assertThat(sleeps).hasValue(1);
  }

  @Test
  void conflictAfterThreeAttemptsSurfacesAsConflict() throws SQLException {
    doThrow(new SQLException("deadlock detected", "40P01")).when(connection).commit();

    assertThatThrownBy(() -> runner.inTransaction(false, () -> 1))
        .isInstanceOf(ConflictException.class)
        .extracting(e -> ((ConflictException) e).code())
        .isEqualTo(ErrorCode.CONFLICT_TRY_AGAIN);
    verify(dataSource, times(RetryPolicy.MAX_ATTEMPTS)).getConnection();
  }

  @Test
  void nestedCallJoinsWithoutNewConnectionOrRetry() throws SQLException {
    AtomicInteger innerRuns = new AtomicInteger();

    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    false,
                    () ->
                        runner.inTransaction(
                            false,
                            () -> {
                              innerRuns.incrementAndGet();
                              assertThat(runner.current().connection()).isSameAs(connection);
                              throw new ConflictException(null);
                            })))
        .isInstanceOf(ConflictException.class);

    // inner conflict is retried only by the outermost transaction: 3 outer attempts, 3 inner runs
    assertThat(innerRuns).hasValue(RetryPolicy.MAX_ATTEMPTS);
    verify(dataSource, times(RetryPolicy.MAX_ATTEMPTS)).getConnection();
  }

  @Test
  void readWriteInsideReadOnlyIsRejected() {
    assertThatThrownBy(() -> runner.inTransaction(true, () -> runner.inTransaction(false, () -> 1)))
        .isInstanceOf(InfrastructureException.class)
        .extracting(e -> ((InfrastructureException) e).code())
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void readOnlyInsideReadWriteJoins() throws SQLException {
    assertThat(runner.inTransaction(false, () -> runner.inTransaction(true, () -> 7))).isEqualTo(7);
    verify(connection).setReadOnly(false);
    verify(connection).commit();
  }

  @Test
  void currentWithoutTransactionIsInternalError() {
    assertThatThrownBy(runner::current)
        .isInstanceOf(InfrastructureException.class)
        .extracting(e -> ((InfrastructureException) e).code())
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void poolTimeoutIsBusy() throws SQLException {
    when(dataSource.getConnection())
        .thenThrow(new java.sql.SQLTransientConnectionException("request timed out"));

    assertThatThrownBy(() -> runner.inTransaction(true, () -> 1))
        .extracting(e -> ((InfrastructureException) e).code())
        .isEqualTo(ErrorCode.BUSY_TRY_AGAIN);
  }

  @Test
  void failedRollbackStillRethrowsTheOriginalFailure() throws SQLException {
    doThrow(new SQLException("connection lost", "08006")).when(connection).rollback();

    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    false,
                    () -> {
                      throw new ValidationException(Map.of());
                    }))
        .isInstanceOf(ValidationException.class);
    assertThat(runner.isActive()).isFalse();
  }
}
