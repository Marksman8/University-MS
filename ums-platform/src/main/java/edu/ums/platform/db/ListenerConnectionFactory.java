package edu.ums.platform.db;

import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.AppConfig.DbAccount;
import edu.ums.platform.error.SqlErrorMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Properties;

/**
 * Opens the single dedicated, non-pooled connection used for {@code LISTEN}. Auto-commit stays on;
 * the connection works in schema {@code ums} with driver error details hidden.
 */
public final class ListenerConnectionFactory {

  /** {@code ApplicationName} reported to PostgreSQL. */
  public static final String APPLICATION_NAME = "ums-listener";

  private final String url;
  private final DbAccount account;
  private final SqlErrorMapper errors;

  /** Creates a factory for the listener account in {@code config}. */
  public ListenerConnectionFactory(AppConfig config, SqlErrorMapper errors) {
    this.url = config.database().url();
    this.account = config.database().listener();
    this.errors = errors;
  }

  /**
   * Opens a new connection. The caller owns and closes it.
   *
   * @throws edu.ums.platform.error.UmsException mapped from the driver failure
   */
  public Connection open() {
    Properties properties = SessionSettings.driverProperties(APPLICATION_NAME);
    properties.setProperty("user", account.user());
    char[] password = account.password().reveal();
    properties.setProperty("password", new String(password));
    Arrays.fill(password, '\0');
    try {
      Connection connection = DriverManager.getConnection(url, properties);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET search_path = " + SessionSettings.SCHEMA);
      } catch (SQLException e) {
        connection.close();
        throw e;
      }
      return connection;
    } catch (SQLException e) {
      throw errors.map(e, false);
    }
  }
}
