package edu.ums.platform.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Binds parameters onto a prepared statement. */
@FunctionalInterface
public interface StatementBinder {

  /** Binder for statements without parameters. */
  StatementBinder NONE = ps -> {};

  /** Sets every parameter of {@code ps}, starting at index 1. */
  void bind(PreparedStatement ps) throws SQLException;
}
