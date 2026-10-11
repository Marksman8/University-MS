package edu.ums.proto.dev.repository;

import edu.ums.api.proto.QueryResult;

/** Executes one already-guarded read-only statement on the console pool. */
public interface ConsoleRepository {

  /** Rows returned at most; one more is fetched to detect truncation. */
  int MAX_ROWS = 200;

  /** Runs {@code sql}; SQL errors come back inside the result, never as an exception. */
  QueryResult run(String sql);
}
