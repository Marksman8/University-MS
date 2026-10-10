package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ums.api.dto.Cursor;
import edu.ums.api.dto.Page;
import edu.ums.api.dto.PageRequest;
import edu.ums.api.error.ErrorCode;
import edu.ums.platform.db.KeysetQuery.Direction;
import edu.ums.platform.db.KeysetQuery.KeyColumn;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.StaleDataException;
import edu.ums.platform.error.UmsException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BaseRepository templates against mocked JDBC (the real-database ITs live in ums-testkit). */
class BaseRepositoryTest {

  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement ps = mock(PreparedStatement.class);
  private final ResultSet rs = mock(ResultSet.class);
  private final TransactionRunner tx =
      new TransactionRunner(
          dataSource,
          new SqlErrorMapper(ConstraintErrorMap.standard()),
          new RetryPolicy(new Random(1), millis -> {}));
  private final Repo repo = new Repo(tx);

  @BeforeEach
  void wire() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeQuery()).thenReturn(rs);
  }

  @Test
  void queryOneReturnsTheRowAndCapsRowsAtTwo() throws SQLException {
    when(rs.next()).thenReturn(true, false);
    when(rs.getLong(1)).thenReturn(42L);

    assertThat(inTx(repo::one)).isEqualTo(42L);
    verify(ps).setMaxRows(2);
    verify(ps).setLong(1, 7L);
  }

  @Test
  void queryOneWithoutRowIsNotFoundAndWithTwoRowsIsInternal() throws SQLException {
    when(rs.next()).thenReturn(false);
    assertThatThrownBy(() -> inTx(repo::one)).isInstanceOf(NotFoundException.class);

    when(rs.next()).thenReturn(true, true, false);
    assertInternal(() -> inTx(repo::optional));
  }

  @Test
  void queryListStopsAtTheLimitAndRejectsOutOfRangeLimits() throws SQLException {
    when(rs.next()).thenReturn(true);
    when(rs.getLong(1)).thenReturn(1L);

    assertThat(inTx(() -> repo.list(3))).hasSize(3);
    verify(ps).setMaxRows(3);
    assertInternal(() -> inTx(() -> repo.list(0)));
    assertInternal(() -> inTx(() -> repo.list(BaseRepository.MAX_LIST_LIMIT + 1)));
  }

  @Test
  void updateExactlyOneDistinguishesZeroOneAndMany() throws SQLException {
    when(ps.executeUpdate()).thenReturn(1, 0, 2);

    inTx(repo::touch);
    assertThatThrownBy(() -> inTx(repo::touch))
        .isInstanceOfSatisfying(
            StaleDataException.class,
            e -> assertThat(e.details()).containsEntry("entity", "thing"));
    assertInternal(() -> inTx(repo::touch));
  }

  @Test
  void casStateBindsStatesAndVersionAndReturnsTheNextVersion() throws SQLException {
    when(ps.executeUpdate()).thenReturn(1);

    assertThat(inTx(() -> repo.cas("thing"))).isEqualTo(4L);
    verify(connection)
        .prepareStatement(
            "UPDATE thing SET state = ?, version = version + 1 WHERE id = ? AND state = ? AND version = ?");
    verify(ps).setString(1, "APPROVED");
    verify(ps).setLong(2, 9L);
    verify(ps).setString(3, "SUBMITTED");
    verify(ps).setLong(4, 3L);
    assertInternal(() -> inTx(() -> repo.cas("thing; DROP TABLE x")));
  }

  @Test
  void batchSkipsEmptyInputSumsCountsAndRejectsOversizedBatches() throws SQLException {
    assertThat(inTx(() -> repo.batch(List.of()))).isZero();
    verify(connection, never()).prepareStatement(anyString());

    when(ps.executeBatch()).thenReturn(new int[] {1, Statement.SUCCESS_NO_INFO, 1});
    assertThat(inTx(() -> repo.batch(List.of("a", "b", "c")))).isEqualTo(2);
    verify(ps).setString(1, "a");

    List<String> tooMany = java.util.Collections.nCopies(BaseRepository.MAX_BATCH_SIZE + 1, "x");
    assertInternal(() -> inTx(() -> repo.batch(tooMany)));
  }

  @Test
  void keysetPageReadsOneExtraRowAndReturnsACursorThatSeeksOnTheNextPage() throws SQLException {
    when(rs.next()).thenReturn(true, true, true, false);
    when(rs.getLong(1)).thenReturn(30L, 20L, 10L);

    Page<Long> first = inTx(() -> repo.page(PageRequest.first(2)));

    assertThat(first.items()).containsExactly(30L, 20L);
    assertThat(first.next()).isPresent();
    verify(ps).setObject(1, "student");
    verify(ps).setInt(2, 3);

    PreparedStatement second = mock(PreparedStatement.class);
    ResultSet secondRows = mock(ResultSet.class);
    when(connection.prepareStatement(anyString())).thenReturn(second);
    when(second.executeQuery()).thenReturn(secondRows);
    when(secondRows.next()).thenReturn(true, false);
    when(secondRows.getLong(1)).thenReturn(10L);

    Optional<Cursor> after = first.next();
    Page<Long> last = inTx(() -> repo.page(new PageRequest(after, 2)));

    assertThat(last.items()).containsExactly(10L);
    assertThat(last.next()).isEmpty();
    verify(second).setObject(1, "student");
    verify(second).setLong(2, 20L);
    verify(second).setInt(3, 3);
  }

  @Test
  void sqlFailuresAreMappedAndRepositoriesNeedATransaction() throws SQLException {
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("fk", "23503"));
    assertThatThrownBy(() -> inTx(repo::one)).isInstanceOf(NotFoundException.class);

    assertInternal(repo::one);
  }

  private <T> T inTx(Supplier<T> work) {
    return tx.inTransaction(false, work);
  }

  private static void assertInternal(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            UmsException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR))
        .isInstanceOf(InfrastructureException.class);
  }

  /** Exposes the protected templates. */
  private static final class Repo extends BaseRepository {

    Repo(TransactionRunner tx) {
      super(tx, new SqlErrorMapper(ConstraintErrorMap.standard()), new CursorCodec());
    }

    long one() {
      return queryOne("SELECT v FROM thing WHERE id = ?", p -> p.setLong(1, 7L), r -> r.getLong(1));
    }

    Optional<Long> optional() {
      return queryOptional("SELECT v FROM thing", StatementBinder.NONE, r -> r.getLong(1));
    }

    List<Long> list(int limit) {
      return queryList("SELECT v FROM thing", StatementBinder.NONE, r -> r.getLong(1), limit);
    }

    Void touch() {
      updateExactlyOne(
          "UPDATE thing SET v = 1 WHERE id = 1", StatementBinder.NONE, Map.of("entity", "thing"));
      return null;
    }

    long cas(String table) {
      return casState(table, 9L, "SUBMITTED", "APPROVED", 3L);
    }

    int batch(List<String> items) {
      return batch("INSERT INTO thing (v) VALUES (?)", items, (p, item) -> p.setString(1, item));
    }

    Page<Long> page(PageRequest request) {
      return queryPage(
          new KeysetQuery<>(
              "SELECT id FROM thing",
              "kind = ?",
              List.of("student"),
              List.of(new KeyColumn("id", KeyType.LONG)),
              Direction.DESC,
              r -> r.getLong(1),
              id -> List.of(id)),
          request);
    }
  }
}
