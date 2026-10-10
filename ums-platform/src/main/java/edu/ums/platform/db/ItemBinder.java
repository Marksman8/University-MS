package edu.ums.platform.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Binds one batch item's parameters onto a prepared statement. */
@FunctionalInterface
public interface ItemBinder<T> {

  /** Sets every parameter of {@code ps} for {@code item}, starting at index 1. */
  void bind(PreparedStatement ps, T item) throws SQLException;
}
