package edu.ums.platform.db;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.AppConfig.DbAccount;
import edu.ums.platform.config.Environment;
import edu.ums.platform.error.InfrastructureException;
import java.util.Arrays;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Applies Flyway migrations from {@code classpath:db/migration} to schema {@code ums} as the schema
 * owner ({@code ums_owner}), never as an application role. Validates on migrate; {@code clean} is
 * allowed only in {@link Environment#TEST}. The schema itself is created by {@code
 * scripts/db/create_roles.sql}.
 */
public final class MigrationRunner {

  /** Flyway location of the versioned migrations. */
  public static final String LOCATION = "classpath:db/migration";

  private final Flyway flyway;
  private final Environment environment;

  /** Creates a runner over a data source that connects as the schema owner. */
  public MigrationRunner(DataSource ownerDataSource, Environment environment) {
    this.environment = Objects.requireNonNull(environment, "environment");
    this.flyway =
        Flyway.configure(MigrationRunner.class.getClassLoader())
            .dataSource(Objects.requireNonNull(ownerDataSource, "ownerDataSource"))
            .locations(LOCATION)
            .schemas(SessionSettings.SCHEMA)
            .defaultSchema(SessionSettings.SCHEMA)
            .createSchemas(false)
            .validateOnMigrate(true)
            .cleanDisabled(environment != Environment.TEST)
            .load();
  }

  /** Creates a runner connecting with the owner account from {@code config}. */
  public static MigrationRunner forOwner(AppConfig config) {
    DbAccount owner = config.database().owner();
    PGSimpleDataSource ds = new PGSimpleDataSource();
    ds.setUrl(config.database().url());
    ds.setUser(owner.user());
    char[] password = owner.password().reveal();
    ds.setPassword(new String(password));
    Arrays.fill(password, '\0');
    ds.setLogServerErrorDetail(false);
    ds.setApplicationName("ums-migrations");
    return new MigrationRunner(ds, config.environment());
  }

  /**
   * Applies pending migrations.
   *
   * @return the number of migrations executed (0 when already current)
   * @throws InfrastructureException {@code INTERNAL_ERROR} if validation or a migration fails
   */
  public int migrate() {
    try {
      return flyway.migrate().migrationsExecuted;
    } catch (FlywayException e) {
      throw new InfrastructureException(ErrorCode.INTERNAL_ERROR, e);
    }
  }

  /** Returns the number of migrations not yet applied. */
  public int pendingCount() {
    try {
      return flyway.info().pending().length;
    } catch (FlywayException e) {
      throw new InfrastructureException(ErrorCode.INTERNAL_ERROR, e);
    }
  }

  /**
   * Drops every object in schema {@code ums}. Test environment only.
   *
   * @throws InfrastructureException {@code INTERNAL_ERROR} outside {@link Environment#TEST}
   */
  public void clean() {
    if (environment != Environment.TEST) {
      throw new InfrastructureException(
          ErrorCode.INTERNAL_ERROR,
          new IllegalStateException("clean is disabled in " + environment));
    }
    try {
      flyway.clean();
    } catch (FlywayException e) {
      throw new InfrastructureException(ErrorCode.INTERNAL_ERROR, e);
    }
  }
}
