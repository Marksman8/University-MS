package edu.ums.testkit;

import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.AppConfig.DatabaseConfig;
import edu.ums.platform.config.AppConfig.DbAccount;
import edu.ums.platform.config.AppConfig.PathsConfig;
import edu.ums.platform.config.AppConfig.PolicyDefaults;
import edu.ums.platform.config.AppConfig.PoolConfig;
import edu.ums.platform.config.Environment;
import edu.ums.platform.config.Secret;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Integration-test database access from {@code UMS_TEST_DB_URL}, {@code UMS_TEST_DB_USER} and
 * {@code UMS_TEST_DB_PASSWORD} (CLAUDE.md §6). The test user administers the test database; role
 * behaviour is exercised with {@code SET ROLE}, so role passwords are never needed. Missing
 * variables fail the test with a clear message; integration tests are never silently skipped.
 */
public final class TestDatabase {

  private TestDatabase() {}

  /** Returns {@code UMS_TEST_DB_URL}. */
  public static String url() {
    return required("UMS_TEST_DB_URL");
  }

  /** Returns {@code UMS_TEST_DB_USER}. */
  public static String user() {
    return required("UMS_TEST_DB_USER");
  }

  /** Returns {@code UMS_TEST_DB_PASSWORD}, or an empty string (trust authentication). */
  public static String password() {
    String value = System.getenv("UMS_TEST_DB_PASSWORD");
    return value == null ? "" : value;
  }

  /** Opens an admin connection (auto-commit) with driver error details hidden. */
  public static Connection admin() throws SQLException {
    return admin(true);
  }

  /** Opens an admin connection; {@code hideDetail=false} keeps the driver's default detail. */
  public static Connection admin(boolean hideDetail) throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("user", user());
    properties.setProperty("password", password());
    properties.setProperty("logServerErrorDetail", Boolean.toString(!hideDetail));
    properties.setProperty("ApplicationName", "ums-testkit");
    return DriverManager.getConnection(url(), properties);
  }

  /** Opens a connection acting as {@code role} (admin login, {@code SET ROLE}, schema ums). */
  public static Connection as(String role) throws SQLException {
    Connection connection = admin();
    try (PreparedStatement setRole =
            connection.prepareStatement("SELECT set_config('role', ?, false)");
        Statement st = connection.createStatement()) {
      setRole.setString(1, role);
      setRole.execute();
      st.execute("SET search_path = ums");
    } catch (SQLException e) {
      connection.close();
      throw e;
    }
    return connection;
  }

  /** Returns a data source whose connections act as {@code ums_owner}, for Flyway. */
  public static DataSource owner() {
    return new RoleDataSource("ums_owner");
  }

  /** Runs statements as the admin. */
  public static void exec(String... sql) throws SQLException {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      for (String s : sql) {
        statement.execute(s);
      }
    }
  }

  /** Config whose every account is the test user, with the given statement timeout. */
  public static AppConfig config(Duration statementTimeout) {
    return config(statementTimeout, Duration.ofSeconds(5), Duration.ofSeconds(5));
  }

  /** Config whose every account is the test user, with explicit timeouts. */
  public static AppConfig config(
      Duration statementTimeout, Duration lockTimeout, Duration connectionTimeout) {
    DbAccount account = new DbAccount(user(), Secret.of(password()));
    return new AppConfig(
        Environment.TEST,
        new DatabaseConfig(url(), account, account, account, account, account),
        new PoolConfig(
            4, 2, connectionTimeout, statementTimeout, lockTimeout, Duration.ofSeconds(15)),
        new PolicyDefaults(
            26, 75, Duration.ofHours(48), 5, Duration.ofMinutes(15), Duration.ofMinutes(30)),
        new PathsConfig(
            Path.of("target/backups"), Path.of("target/backups2"), Path.of("target/logs")));
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          name
              + " must be set to run integration tests (README: Running integration tests locally)");
    }
    return value;
  }

  /** Minimal DataSource handing out {@link #as(String)} connections. */
  private record RoleDataSource(String role) implements DataSource {

    @Override
    public Connection getConnection() throws SQLException {
      return as(role);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return as(role);
    }

    @Override
    public PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
      // not used by Flyway
    }

    @Override
    public void setLoginTimeout(int seconds) {
      // not used by Flyway
    }

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
      throw new SQLException("not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
      return false;
    }
  }
}
