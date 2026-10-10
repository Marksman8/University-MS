package edu.ums.platform.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Every configuration key. Non-secret keys come from properties files and can be overridden by an
 * environment variable named {@code UMS_} + the property name after {@code ums.}, upper-cased, with
 * {@code .} and {@code -} replaced by {@code _} (e.g. {@code ums.db.url} → {@code UMS_DB_URL}).
 * Secrets and {@code UMS_ENV} come from environment variables only (master prompt §11).
 */
public enum ConfigKey {
  DB_URL("ums.db.url"),
  APP_USER("ums.db.user"),
  CONSOLE_USER("ums.db.console.user"),
  LISTENER_USER("ums.db.listener.user"),
  OWNER_USER("ums.db.owner.user"),
  BACKUP_USER("ums.db.backup.user"),
  APP_PASSWORD(null, "UMS_DB_PASSWORD"),
  CONSOLE_PASSWORD(null, "UMS_CONSOLE_DB_PASSWORD"),
  LISTENER_PASSWORD(null, "UMS_LISTENER_DB_PASSWORD"),
  OWNER_PASSWORD(null, "UMS_OWNER_DB_PASSWORD"),
  BACKUP_PASSWORD(null, "UMS_BACKUP_DB_PASSWORD"),
  APP_POOL_MAX_SIZE("ums.pool.app.max-size"),
  CONSOLE_POOL_MAX_SIZE("ums.pool.console.max-size"),
  CONNECTION_TIMEOUT("ums.pool.connection-timeout"),
  STATEMENT_TIMEOUT("ums.db.statement-timeout"),
  LOCK_TIMEOUT("ums.db.lock-timeout"),
  CONSOLE_STATEMENT_TIMEOUT("ums.db.console.statement-timeout"),
  CREDIT_LIMIT("ums.policy.credit-limit"),
  ATTENDANCE_THRESHOLD_PERCENT("ums.policy.attendance-threshold-percent"),
  ATTENDANCE_EDIT_WINDOW("ums.policy.attendance-edit-window"),
  LOCKOUT_MAX_FAILURES("ums.policy.lockout.max-failures"),
  LOCKOUT_DURATION("ums.policy.lockout.duration"),
  SESSION_IDLE_TIMEOUT("ums.policy.session-idle-timeout"),
  BACKUP_DIR("ums.backup.dir"),
  BACKUP_SECOND_DIR("ums.backup.second-dir"),
  LOG_DIR("ums.log.dir");

  /** Environment variable that selects the {@link Environment}; not a {@link ConfigKey}. */
  public static final String ENV_VARIABLE = "UMS_ENV";

  private final String property;
  private final String envVar;

  ConfigKey(String property) {
    this(property, deriveEnvVar(property));
  }

  ConfigKey(String property, String envVar) {
    this.property = property;
    this.envVar = envVar;
  }

  /** Returns the properties-file name, empty for secrets (environment only). */
  public Optional<String> property() {
    return Optional.ofNullable(property);
  }

  /** Returns the overriding (or, for secrets, the only) environment variable name. */
  public String envVar() {
    return envVar;
  }

  /** Returns {@code true} for passwords, which are read from the environment only. */
  public boolean isSecret() {
    return property == null;
  }

  /** Returns the name to show in error messages: the property, or the variable for secrets. */
  public String displayName() {
    return property == null ? envVar : property + " (" + envVar + ")";
  }

  private static String deriveEnvVar(String property) {
    String tail = property.substring("ums.".length());
    return "UMS_" + tail.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
  }
}
