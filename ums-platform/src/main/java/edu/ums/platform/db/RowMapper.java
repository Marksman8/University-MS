package edu.ums.platform.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps the current row of a {@link ResultSet} to an object. */
@FunctionalInterface
public interface RowMapper<T> {

  /**
   * Maps the row the cursor is on. Must not move the cursor.
   *
   * @return the mapped object, never {@code null}
   */
  T map(ResultSet rs) throws SQLException;
}
