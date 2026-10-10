package edu.ums.platform.db;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A keyset-paged query: {@code select} + optional {@code where} + a seek predicate on the sort key,
 * ordered by the sort key, limited. All key columns sort in one {@code direction}; the last key
 * column must be unique (normally {@code id}) so pages never overlap.
 *
 * @param select {@code SELECT ... FROM ...} without {@code WHERE}, {@code ORDER BY} or {@code
 *     LIMIT}
 * @param where filter condition with {@code ?} placeholders, or empty
 * @param whereParams values for the {@code where} placeholders, bound in order
 * @param keys sort-key columns, most significant first; last one unique
 * @param direction sort direction of every key column
 * @param mapper row mapper
 * @param keyOf extracts the sort-key values from a mapped row, in {@code keys} order
 */
public record KeysetQuery<T>(
    String select,
    String where,
    List<Object> whereParams,
    List<KeyColumn> keys,
    Direction direction,
    RowMapper<T> mapper,
    Function<T, List<Object>> keyOf) {

  /** Validates the components; column names must be plain (optionally qualified) identifiers. */
  public KeysetQuery {
    Objects.requireNonNull(select, "select");
    Objects.requireNonNull(where, "where");
    whereParams = List.copyOf(whereParams);
    keys = List.copyOf(keys);
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(mapper, "mapper");
    Objects.requireNonNull(keyOf, "keyOf");
    if (keys.isEmpty()) {
      throw new IllegalArgumentException("at least one key column is required");
    }
    for (KeyColumn key : keys) {
      if (!SqlIdentifiers.isColumn(key.column())) {
        throw new IllegalArgumentException("invalid key column: " + key.column());
      }
    }
  }

  /** One sort-key column. */
  public record KeyColumn(String column, KeyType type) {

    /** Rejects {@code null} components. */
    public KeyColumn {
      Objects.requireNonNull(column, "column");
      Objects.requireNonNull(type, "type");
    }
  }

  /** Sort direction shared by every key column. */
  public enum Direction {
    ASC(">"),
    DESC("<");

    private final String seekOperator;

    Direction(String seekOperator) {
      this.seekOperator = seekOperator;
    }

    String seekOperator() {
      return seekOperator;
    }
  }

  /** Builds the SQL; {@code withSeek} adds the {@code (k1, k2) <op> (?, ?)} predicate. */
  String sql(boolean withSeek) {
    String columns = String.join(", ", keys.stream().map(KeyColumn::column).toList());
    String placeholders = String.join(", ", keys.stream().map(k -> "?").toList());
    StringBuilder sql = new StringBuilder(select.length() + where.length() + 128).append(select);
    String seek = "(" + columns + ") " + direction.seekOperator() + " (" + placeholders + ")";
    if (!where.isBlank() && withSeek) {
      sql.append(" WHERE (").append(where).append(") AND ").append(seek);
    } else if (!where.isBlank()) {
      sql.append(" WHERE ").append(where);
    } else if (withSeek) {
      sql.append(" WHERE ").append(seek);
    }
    String order =
        String.join(", ", keys.stream().map(k -> k.column() + " " + direction.name()).toList());
    return sql.append(" ORDER BY ").append(order).append(" LIMIT ?").toString();
  }
}
