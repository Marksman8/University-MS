package edu.ums.platform.config;

import edu.ums.platform.config.AppConfig.DatabaseConfig;
import edu.ums.platform.config.AppConfig.DbAccount;
import edu.ums.platform.config.AppConfig.PathsConfig;
import edu.ums.platform.config.AppConfig.PolicyDefaults;
import edu.ums.platform.config.AppConfig.PoolConfig;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link AppConfig} from {@code application.properties}, then {@code
 * application-<env>.properties}, then environment variables. Validates every key and throws one
 * {@link ConfigException} listing all problems. Logs only the environment name, never values.
 */
// Complexity: time O(k + p) per load (k config keys, p properties read); space O(p)
public final class ConfigLoader {

  static final String BASE_RESOURCE = "application.properties";

  private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

  private final ConfigSource source;
  private final Map<String, String> environment;

  /**
   * Creates a loader.
   *
   * @param source where properties resources are read from
   * @param environment environment variables (normally {@code System.getenv()})
   */
  public ConfigLoader(ConfigSource source, Map<String, String> environment) {
    this.source = source;
    this.environment = Map.copyOf(environment);
  }

  /**
   * Loads and validates the configuration.
   *
   * @throws ConfigException listing every missing or invalid key
   */
  public AppConfig load() {
    Environment env = selectEnvironment();
    Map<String, String> base = source.load(BASE_RESOURCE).orElse(Map.of());
    Map<String, String> profile =
        env == null ? Map.of() : source.load(env.profileResource()).orElse(Map.of());
    ConfigReader reader = new ConfigReader(new ConfigResolver(base, profile, environment));
    if (env == null) {
      reader.addProblem(
          ConfigKey.ENV_VARIABLE
              + ": must be one of LOCAL, TEST, STAGING, PROD (was '"
              + environment.get(ConfigKey.ENV_VARIABLE)
              + "')");
    }
    AppConfig config = build(env, reader);
    List<String> problems = reader.problems();
    if (!problems.isEmpty()) {
      throw new ConfigException(problems);
    }
    LOG.info("Configuration loaded for environment {}", env);
    return config;
  }

  /** Returns the selected environment: LOCAL when unset, {@code null} when invalid. */
  private Environment selectEnvironment() {
    String raw = environment.get(ConfigKey.ENV_VARIABLE);
    if (raw == null || raw.isBlank()) {
      return Environment.LOCAL;
    }
    return Environment.parse(raw).orElse(null);
  }

  private static AppConfig build(Environment env, ConfigReader r) {
    DatabaseConfig database = database(r);
    PoolConfig pools = pools(r);
    PolicyDefaults policy = policy(r);
    PathsConfig paths =
        new PathsConfig(
            r.path(ConfigKey.BACKUP_DIR),
            r.path(ConfigKey.BACKUP_SECOND_DIR),
            r.path(ConfigKey.LOG_DIR));
    if (!r.problems().isEmpty()) {
      return null;
    }
    return new AppConfig(env, database, pools, policy, paths);
  }

  private static DatabaseConfig database(ConfigReader r) {
    return new DatabaseConfig(
        r.jdbcUrl(ConfigKey.DB_URL),
        account(r, ConfigKey.APP_USER, ConfigKey.APP_PASSWORD),
        account(r, ConfigKey.CONSOLE_USER, ConfigKey.CONSOLE_PASSWORD),
        account(r, ConfigKey.LISTENER_USER, ConfigKey.LISTENER_PASSWORD),
        account(r, ConfigKey.OWNER_USER, ConfigKey.OWNER_PASSWORD),
        account(r, ConfigKey.BACKUP_USER, ConfigKey.BACKUP_PASSWORD));
  }

  private static DbAccount account(ConfigReader r, ConfigKey user, ConfigKey password) {
    return new DbAccount(r.string(user), r.secret(password));
  }

  private static PoolConfig pools(ConfigReader r) {
    Integer appMax = r.intInRange(ConfigKey.APP_POOL_MAX_SIZE, 1, 100);
    Integer consoleMax = r.intInRange(ConfigKey.CONSOLE_POOL_MAX_SIZE, 1, 20);
    return new PoolConfig(
        appMax == null ? 0 : appMax,
        consoleMax == null ? 0 : consoleMax,
        r.positiveDuration(ConfigKey.CONNECTION_TIMEOUT),
        r.positiveDuration(ConfigKey.STATEMENT_TIMEOUT),
        r.positiveDuration(ConfigKey.LOCK_TIMEOUT),
        r.positiveDuration(ConfigKey.CONSOLE_STATEMENT_TIMEOUT));
  }

  private static PolicyDefaults policy(ConfigReader r) {
    Integer credits = r.intInRange(ConfigKey.CREDIT_LIMIT, 1, 100);
    Integer threshold = r.intInRange(ConfigKey.ATTENDANCE_THRESHOLD_PERCENT, 0, 100);
    Integer failures = r.intInRange(ConfigKey.LOCKOUT_MAX_FAILURES, 1, 100);
    return new PolicyDefaults(
        credits == null ? 0 : credits,
        threshold == null ? 0 : threshold,
        r.positiveDuration(ConfigKey.ATTENDANCE_EDIT_WINDOW),
        failures == null ? 0 : failures,
        r.positiveDuration(ConfigKey.LOCKOUT_DURATION),
        r.positiveDuration(ConfigKey.SESSION_IDLE_TIMEOUT));
  }
}
