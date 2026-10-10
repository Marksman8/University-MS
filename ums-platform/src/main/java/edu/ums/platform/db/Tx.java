package edu.ums.platform.db;

import java.sql.Connection;
import java.util.Objects;

/**
 * The transaction bound to the current thread. Repositories use its connection but never close,
 * commit or roll it back; {@link TransactionRunner} owns the lifecycle.
 *
 * @param connection the transaction's connection
 * @param readOnly whether the transaction was opened read-only
 */
public record Tx(Connection connection, boolean readOnly) {

  /** Rejects a {@code null} connection. */
  public Tx {
    Objects.requireNonNull(connection, "connection");
  }
}
