package edu.ums.platform.db;

import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.AppConfig.DatabaseConfig;
import edu.ums.platform.config.AppConfig.DbAccount;
import edu.ums.platform.config.AppConfig.PathsConfig;
import edu.ums.platform.config.AppConfig.PolicyDefaults;
import edu.ums.platform.config.AppConfig.PoolConfig;
import edu.ums.platform.config.Environment;
import edu.ums.platform.config.Secret;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

/**
 * Integration-test database access, from {@code UMS_TEST_DB_URL}, {@code UMS_TEST_DB_USER} and
 * {@code UMS_TEST_DB_PASSWORD} (CLAUDE.md §6). Missing variables fail the test with a clear
 * message; integration tests are never silently skipped.
 */
final class DbTestSupport {

  private DbTestSupport() {}

  static String url() {
    return required("UMS_TEST_DB_URL");
  }

  static String user() {
    return required("UMS_TEST_DB_USER");
  }

  static String password() {
    String value = System.getenv("UMS_TEST_DB_PASSWORD");
    return value == null ? "" : value;
  }

  /** Config whose every account is the test user; console pool gets a 15 s statement timeout. */
  static AppConfig config(Duration statementTimeout) {
    DbAccount account = new DbAccount(user(), Secret.of(password()));
    return new AppConfig(
        Environment.TEST,
        new DatabaseConfig(url(), account, account, account, account, account),
        new PoolConfig(
            4,
            2,
            Duration.ofSeconds(5),
            statementTimeout,
            Duration.ofSeconds(5),
            Duration.ofSeconds(15)),
        new PolicyDefaults(
            26, 75, Duration.ofHours(48), 5, Duration.ofMinutes(15), Duration.ofMinutes(30)),
        new PathsConfig(
            Path.of("target/backups"), Path.of("target/backups2"), Path.of("target/logs")));
  }

  /** Opens a plain admin connection (auto-commit) for fixture setup. */
  static Connection admin(boolean hideDetail) throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("user", user());
    properties.setProperty("password", password());
    properties.setProperty("logServerErrorDetail", Boolean.toString(!hideDetail));
    return DriverManager.getConnection(url(), properties);
  }

  /** Runs DDL/DML statements as the admin. */
  static void exec(String... sql) throws SQLException {
    try (Connection connection = admin(true);
        Statement statement = connection.createStatement()) {
      for (String s : sql) {
        statement.execute(s);
      }
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          name + " must be set to run integration tests (CLAUDE.md §6)");
    }
    return value;
  }
}
