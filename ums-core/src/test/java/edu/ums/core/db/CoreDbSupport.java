package edu.ums.core.db;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Integration-test database access for ums-core, from {@code UMS_TEST_DB_URL/USER/PASSWORD}. The
 * test user is an administrator of the test database; role-specific behaviour is exercised with
 * {@code SET ROLE}, so the role passwords never need to be known. Moves to ums-testkit in Step 12.
 */
final class CoreDbSupport {

  private CoreDbSupport() {}

  /** Opens an admin connection (auto-commit, driver detail hidden). */
  static Connection admin() throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("user", required("UMS_TEST_DB_USER"));
    String password = System.getenv("UMS_TEST_DB_PASSWORD");
    properties.setProperty("password", password == null ? "" : password);
    properties.setProperty("logServerErrorDetail", "false");
    return DriverManager.getConnection(required("UMS_TEST_DB_URL"), properties);
  }

  /** Opens a connection acting as {@code role} (admin login, then {@code SET ROLE}). */
  static Connection as(String role) throws SQLException {
    Connection connection = admin();
    try (Statement st = connection.createStatement()) {
      st.execute("SET ROLE " + role);
      st.execute("SET search_path = ums");
    } catch (SQLException e) {
      connection.close();
      throw e;
    }
    return connection;
  }

  /** A data source whose connections act as {@code ums_owner}, for Flyway. */
  static DataSource owner() {
    return new RoleDataSource("ums_owner");
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          name + " must be set to run integration tests (CLAUDE.md §6)");
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
      // not used
    }

    @Override
    public void setLoginTimeout(int seconds) {
      // not used
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
