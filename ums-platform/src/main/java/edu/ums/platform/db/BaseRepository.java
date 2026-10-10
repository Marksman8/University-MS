package edu.ums.platform.db;

import edu.ums.api.dto.Cursor;
import edu.ums.api.dto.Page;
import edu.ums.api.dto.PageRequest;
import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.StaleDataException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Template-method base for every repository. Subclasses supply SQL, binders and row mappers; this
 * class owns statement creation, binding, bounded result handling and exception mapping. Every
 * operation runs on the current thread's transaction ({@link TransactionRunner#current()}); there
 * is no auto-commit path. Only {@link PreparedStatement}s are used.
 */
// Complexity: time O(r) per query for r rows read (r <= limit); space O(r) — bounded by the
// caller's limit (<= MAX_LIST_LIMIT) or page size (<= 200)
public abstract class BaseRepository {

  /** Largest {@code limit} accepted by {@link #queryList}. */
  public static final int MAX_LIST_LIMIT = 10_000;

  /** Largest number of items accepted by {@link #batch}. */
  public static final int MAX_BATCH_SIZE = 1_000;

  private final TransactionRunner transactions;
  private final SqlErrorMapper errors;
  private final CursorCodec cursors;

  protected BaseRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    this.transactions = transactions;
    this.errors = errors;
    this.cursors = cursors;
  }

  /**
   * Returns exactly one row.
   *
   * @throws NotFoundException if there is no row
   * @throws InfrastructureException {@code INTERNAL_ERROR} if there is more than one
   */
  protected final <T> T queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
    return queryOptional(sql, binder, mapper).orElseThrow(() -> new NotFoundException(Map.of()));
  }

  /**
   * Returns zero or one row.
   *
   * @throws InfrastructureException {@code INTERNAL_ERROR} if there is more than one
   */
  protected final <T> Optional<T> queryOptional(
      String sql, StatementBinder binder, RowMapper<T> mapper) {
    List<T> rows = queryList(sql, binder, mapper, 2);
    if (rows.size() > 1) {
      throw internal("query expected at most one row");
    }
    return rows.stream().findFirst();
  }

  /** Returns at most {@code limit} rows, {@code 1 <= limit <= MAX_LIST_LIMIT}. */
  protected final <T> List<T> queryList(
      String sql, StatementBinder binder, RowMapper<T> mapper, int limit) {
    if (limit < 1 || limit > MAX_LIST_LIMIT) {
      throw internal("limit out of range: " + limit);
    }
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setMaxRows(limit);
      binder.bind(ps);
      return readRows(ps, mapper, limit);
    } catch (SQLException e) {
      throw errors.map(e, false);
    }
  }

  /** Returns one keyset page; reads {@code size + 1} rows to detect whether another page exists. */
  protected final <T> Page<T> queryPage(KeysetQuery<T> query, PageRequest request) {
    Optional<List<Object>> after = request.after().map(c -> cursors.decode(c, query.keys()));
    int size = request.size();
    try (PreparedStatement ps = connection().prepareStatement(query.sql(after.isPresent()))) {
      int index = 1;
      for (Object param : query.whereParams()) {
        ps.setObject(index++, param);
      }
      if (after.isPresent()) {
        for (int i = 0; i < query.keys().size(); i++) {
          query.keys().get(i).type().bind(ps, index++, after.get().get(i));
        }
      }
      ps.setInt(index, size + 1);
      List<T> rows = readRows(ps, query.mapper(), size + 1);
      if (rows.size() <= size) {
        return Page.last(rows);
      }
      List<T> items = rows.subList(0, size);
      Cursor next = cursors.encode(query.keyOf().apply(items.get(size - 1)), query.keys());
      return new Page<>(items, Optional.of(next));
    } catch (SQLException e) {
      throw errors.map(e, false);
    }
  }

  /** Runs an INSERT/UPDATE/DELETE and returns the affected row count. */
  protected final int update(String sql, StatementBinder binder) {
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      binder.bind(ps);
      return ps.executeUpdate();
    } catch (SQLException e) {
      throw errors.map(e, false);
    }
  }

  /**
   * Runs an UPDATE/DELETE that must affect exactly one row.
   *
   * @throws StaleDataException if no row matched (version or state changed, or row gone)
   * @throws InfrastructureException {@code INTERNAL_ERROR} if more than one row matched
   */
  protected final void updateExactlyOne(
      String sql, StatementBinder binder, Map<String, Object> details) {
    int rows = update(sql, binder);
    if (rows == 0) {
      throw new StaleDataException(details);
    }
    if (rows > 1) {
      throw internal("expected exactly one row, updated " + rows);
    }
  }

  /** Runs one statement for each of at most {@code MAX_BATCH_SIZE} items; returns total rows. */
  protected final <I> int batch(String sql, List<I> items, ItemBinder<I> binder) {
    if (items.size() > MAX_BATCH_SIZE) {
      throw internal("batch too large: " + items.size());
    }
    if (items.isEmpty()) {
      return 0;
    }
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      for (I item : items) {
        binder.bind(ps, item);
        ps.addBatch();
      }
      int total = 0;
      for (int count : ps.executeBatch()) {
        total += Math.max(count, 0);
      }
      return total;
    } catch (SQLException e) {
      throw errors.map(e, false);
    }
  }

  /**
   * Compare-and-set state change: {@code UPDATE <table> SET state=?, version=version+1 WHERE id=?
   * AND state=? AND version=?}.
   *
   * @param table a constant table name owned by the subclass (validated as an identifier)
   * @return the new version
   * @throws StaleDataException if the row is not in {@code fromState} at {@code version}
   */
  protected final long casState(
      String table, long id, String fromState, String toState, long version) {
    if (!SqlIdentifiers.isPlain(table)) {
      throw internal("invalid table name");
    }
    String sql =
        "UPDATE "
            + table
            + " SET state = ?, version = version + 1"
            + " WHERE id = ? AND state = ? AND version = ?";
    updateExactlyOne(
        sql,
        ps -> {
          ps.setString(1, toState);
          ps.setLong(2, id);
          ps.setString(3, fromState);
          ps.setLong(4, version);
        },
        Map.of("entity", table));
    return version + 1;
  }

  private Connection connection() {
    return transactions.current().connection();
  }

  private static <T> List<T> readRows(PreparedStatement ps, RowMapper<T> mapper, int limit)
      throws SQLException {
    try (ResultSet rs = ps.executeQuery()) {
      List<T> rows = new ArrayList<>(Math.min(limit, 64));
      while (rows.size() < limit && rs.next()) {
        rows.add(mapper.map(rs));
      }
      return rows;
    }
  }

  private static InfrastructureException internal(String reason) {
    return new InfrastructureException(ErrorCode.INTERNAL_ERROR, new IllegalStateException(reason));
  }
}
