package edu.ums.platform.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.AppConfig.DbAccount;
import edu.ums.platform.error.DataUnavailableException;
import edu.ums.platform.error.SqlErrorMapper;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;

/**
 * The two HikariCP pools: {@code ums-app} (all normal work) and {@code ums-console} (dev console
 * only, its own DB role). Every connection runs in schema {@code ums} with statement and lock
 * timeouts, and with driver error details hidden.
 */
public final class DataSources implements AutoCloseable {

  /** Pool name of the application pool (also the metrics label). */
  public static final String APP_POOL = "ums-app";

  /** Pool name of the dev-console pool (also the metrics label). */
  public static final String CONSOLE_POOL = "ums-console";

  private final HikariDataSource app;
  private final HikariDataSource console;

  private DataSources(HikariDataSource app, HikariDataSource console) {
    this.app = app;
    this.console = console;
  }

  /**
   * Opens both pools. Fails fast when the database is unreachable.
   *
   * @throws DataUnavailableException or another mapped exception if a pool cannot start
   */
  public static DataSources create(AppConfig config, SqlErrorMapper errors) {
    AppConfig.PoolConfig pools = config.pools();
    HikariConfig appConfig =
        pool(APP_POOL, config.database().url(), config.database().app(), pools.appMaxSize(), pools);
    appConfig.setConnectionInitSql(
        SessionSettings.initSql(pools.statementTimeout(), pools.lockTimeout()));
    HikariConfig consoleConfig =
        pool(
            CONSOLE_POOL,
            config.database().url(),
            config.database().console(),
            pools.consoleMaxSize(),
            pools);
    consoleConfig.setConnectionInitSql(
        SessionSettings.initSql(pools.consoleStatementTimeout(), pools.lockTimeout()));

    HikariDataSource app = open(appConfig, errors);
    boolean started = false;
    try {
      DataSources sources = new DataSources(app, open(consoleConfig, errors));
      started = true;
      return sources;
    } finally {
      if (!started) {
        app.close();
      }
    }
  }

  /** Returns the application pool. */
  public DataSource app() {
    return app;
  }

  /** Returns the dev-console pool. */
  public DataSource console() {
    return console;
  }

  /** Returns the pool names, for metrics. */
  public List<String> poolNames() {
    return List.of(APP_POOL, CONSOLE_POOL);
  }

  /** Closes both pools, releasing every connection. Idempotent. */
  @Override
  public void close() {
    console.close();
    app.close();
  }

  private static HikariConfig pool(
      String name, String url, DbAccount account, int maxSize, AppConfig.PoolConfig pools) {
    HikariConfig hc = new HikariConfig();
    hc.setPoolName(name);
    hc.setJdbcUrl(url);
    hc.setUsername(account.user());
    char[] password = account.password().reveal();
    hc.setPassword(new String(password));
    Arrays.fill(password, '\0');
    hc.setMaximumPoolSize(maxSize);
    hc.setConnectionTimeout(pools.connectionTimeout().toMillis());
    hc.setRegisterMbeans(false);
    hc.setDataSourceProperties(SessionSettings.driverProperties(name));
    return hc;
  }

  private static HikariDataSource open(HikariConfig config, SqlErrorMapper errors) {
    try {
      return new HikariDataSource(config);
    } catch (PoolInitializationException e) {
      if (e.getCause() instanceof SQLException sql) {
        throw errors.map(sql, false);
      }
      throw new DataUnavailableException(e);
    }
  }
}
