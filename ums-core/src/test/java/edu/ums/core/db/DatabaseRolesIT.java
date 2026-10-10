package edu.ums.core.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.platform.config.Environment;
import edu.ums.platform.db.MigrationRunner;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Privileges granted by scripts/db/create_roles.sql plus V1, checked via SET ROLE. */
class DatabaseRolesIT {

  private static final String PERMISSION_DENIED = "42501";

  @BeforeAll
  static void migrate() {
    new MigrationRunner(CoreDbSupport.owner(), Environment.TEST).migrate();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ums.it_forbidden", "public.it_forbidden"})
  void appCannotCreateTables(String table) throws SQLException {
    assertDenied("ums_app", "CREATE TABLE " + table + " (id int)");
  }

  @Test
  void appCanReadAndWriteApplicationTables() throws SQLException {
    try (Connection c = CoreDbSupport.as("ums_app");
        Statement st = c.createStatement()) {
      st.execute(
          "INSERT INTO activity_log (role, action, outcome) VALUES ('SYSTEM', 'IT_APP', 'SUCCESS')");
      st.execute("UPDATE feature_flag SET updated_at = now() WHERE key = 'NOTICES'");
      try (ResultSet rs =
          st.executeQuery("SELECT count(*) FROM activity_log WHERE action = 'IT_APP'")) {
        rs.next();
        assertThat(rs.getLong(1)).isEqualTo(1);
      }
      st.execute("DELETE FROM activity_log WHERE action = 'IT_APP'");
    }
  }

  @Test
  void appCanReadButNotChangeMigrationHistory() throws SQLException {
    try (Connection c = CoreDbSupport.as("ums_app");
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM flyway_schema_history")) {
      rs.next();
      assertThat(rs.getLong(1)).isPositive();
    }
    assertDenied("ums_app", "DELETE FROM flyway_schema_history");
  }

  @Test
  void devConsoleCannotInsertButCanSelect() throws SQLException {
    assertDenied(
        "ums_dev_console",
        "INSERT INTO activity_log (role, action, outcome) VALUES ('DEV', 'IT', 'SUCCESS')");
    assertDenied("ums_dev_console", "UPDATE feature_flag SET enabled = false");
    assertDenied("ums_dev_console", "CREATE TABLE ums.it_forbidden (id int)");
    try (Connection c = CoreDbSupport.as("ums_dev_console");
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM feature_flag")) {
      rs.next();
      assertThat(rs.getLong(1)).isEqualTo(5);
    }
  }

  @Test
  void devConsoleHasFifteenSecondStatementTimeout() throws SQLException {
    try (Connection c = CoreDbSupport.admin();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT rolconfig::text FROM pg_roles WHERE rolname = 'ums_dev_console'")) {
      rs.next();
      assertThat(rs.getString(1)).contains("statement_timeout=15s");
    }
  }

  @Test
  void backupIsReadOnly() throws SQLException {
    assertDenied("ums_backup", "DELETE FROM change_history");
    try (Connection c = CoreDbSupport.as("ums_backup");
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM backup_log")) {
      assertThat(rs.next()).isTrue();
    }
  }

  @Test
  void applicationRolesAreNotPrivileged() throws SQLException {
    try (Connection c = CoreDbSupport.admin();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT count(*) FROM pg_roles WHERE rolname IN"
                    + " ('ums_owner','ums_app','ums_dev_console','ums_backup')"
                    + " AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb AND rolcanlogin")) {
      rs.next();
      assertThat(rs.getLong(1)).isEqualTo(4);
    }
  }

  private static void assertDenied(String role, String sql) throws SQLException {
    try (Connection c = CoreDbSupport.as(role);
        Statement st = c.createStatement()) {
      assertThatThrownBy(() -> st.execute(sql))
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(PERMISSION_DENIED));
    }
  }
}
