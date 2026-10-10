package edu.ums.platform.db;

import java.time.Duration;
import java.util.Properties;

/** Connection-level settings shared by the pools and the listener connection. Pure helpers. */
final class SessionSettings {

  /** Schema every UMS connection works in. */
  static final String SCHEMA = "ums";

  private SessionSettings() {}

  /**
   * Returns the SQL run once per new pooled connection.
   *
   * @return e.g. {@code SET search_path = ums; SET statement_timeout = '10000ms'; SET lock_timeout
   *     = '5000ms'}
   */
  static String initSql(Duration statementTimeout, Duration lockTimeout) {
    return "SET search_path = "
        + SCHEMA
        + "; SET statement_timeout = '"
        + statementTimeout.toMillis()
        + "ms'; SET lock_timeout = '"
        + lockTimeout.toMillis()
        + "ms'";
  }

  /**
   * Returns pgjdbc properties common to every connection. {@code logServerErrorDetail=false} keeps
   * row values (e.g. {@code Key (username)=(alice)}) out of exception messages and therefore out of
   * logs; constraint names and SQLStates are still reported.
   */
  static Properties driverProperties(String applicationName) {
    Properties properties = new Properties();
    properties.setProperty("logServerErrorDetail", "false");
    properties.setProperty("ApplicationName", applicationName);
    properties.setProperty("connectTimeout", "5");
    return properties;
  }
}
