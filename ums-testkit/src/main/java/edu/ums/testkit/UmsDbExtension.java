package edu.ums.testkit;

import edu.ums.platform.config.Environment;
import edu.ums.platform.db.MigrationRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for database integration tests. Once per JVM (kept in the root {@link
 * ExtensionContext.Store}, not a static field) it runs Flyway clean + migrate as {@code ums_owner};
 * before each test it truncates every {@code ums} table except {@code flyway_schema_history} and
 * the seed tables ({@link #SEED_TABLES}). The TRUNCATE statement is generated from the catalog
 * once. Requires the migrations on the test classpath (ums-core or ums-app tests).
 */
public final class UmsDbExtension implements BeforeAllCallback, BeforeEachCallback {

  /** Tables whose rows are seeded by migrations and must survive between tests. */
  public static final Set<String> SEED_TABLES = Set.of("feature_flag");

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(UmsDbExtension.class);

  @Override
  public void beforeAll(ExtensionContext context) {
    truncateStatement(context);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws SQLException {
    String truncate = truncateStatement(context);
    if (!truncate.isEmpty()) {
      TestDatabase.exec(truncate);
    }
  }

  private static String truncateStatement(ExtensionContext context) {
    return context
        .getRoot()
        .getStore(NAMESPACE)
        .computeIfAbsent("truncate-statement", key -> prepareSchema(), String.class);
  }

  /** Clean + migrate, then build the TRUNCATE statement from the catalog. */
  private static String prepareSchema() {
    MigrationRunner migrations = new MigrationRunner(TestDatabase.owner(), Environment.TEST);
    migrations.clean();
    migrations.migrate();
    String sql =
        "SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename)"
            + " FROM pg_tables WHERE schemaname = 'ums' AND tablename <> 'flyway_schema_history'"
            + " AND tablename <> ALL (?)";
    try (Connection connection = TestDatabase.admin();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setArray(1, connection.createArrayOf("text", SEED_TABLES.toArray()));
      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        String tables = rs.getString(1);
        return tables == null ? "" : "TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE";
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot read the ums table list", e);
    }
  }
}
