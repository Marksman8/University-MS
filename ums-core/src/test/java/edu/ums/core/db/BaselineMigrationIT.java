package edu.ums.core.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.platform.config.Environment;
import edu.ums.platform.db.MigrationRunner;
import edu.ums.testkit.TestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** V1 baseline on a blank PostgreSQL 18 schema. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaselineMigrationIT {

  private MigrationRunner runner;
  private int firstRun;
  private int secondRun;

  @BeforeAll
  void migrateBlankSchemaTwice() {
    runner = new MigrationRunner(TestDatabase.owner(), Environment.TEST);
    runner.clean();
    firstRun = runner.migrate();
    secondRun = runner.migrate();
  }

  @Test
  void migratesBlankDatabaseAndSecondRunIsANoOp() {
    assertThat(firstRun).isEqualTo(2); // V1 baseline + V2 listener grants
    assertThat(secondRun).isZero();
    assertThat(runner.pendingCount()).isZero();
  }

  @Test
  void cleanIsRefusedOutsideTest() {
    MigrationRunner prod = new MigrationRunner(TestDatabase.owner(), Environment.PROD);
    assertThatThrownBy(prod::clean).hasMessage("INTERNAL_ERROR");
  }

  @Test
  void createsTheBaselineTablesOwnedByUmsOwner() throws SQLException {
    assertThat(
            rows("SELECT tablename || ':' || tableowner FROM pg_tables WHERE schemaname = 'ums'"))
        .containsExactlyInAnyOrder(
            "activity_log:ums_owner",
            "app_error_log:ums_owner",
            "backup_log:ums_owner",
            "change_history:ums_owner",
            "feature_flag:ums_owner",
            "flyway_schema_history:ums_owner");
  }

  @Test
  void everyConstraintAndIndexIsNamedWithAnApprovedPrefix() throws SQLException {
    List<String> constraints =
        rows(
            "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace"
                + " WHERE n.nspname = 'ums' AND c.conrelid <> 'ums.flyway_schema_history'::regclass"
                + " AND c.contype <> 'n'");
    List<String> indexes =
        rows(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'ums'"
                + " AND tablename <> 'flyway_schema_history'");

    assertThat(constraints).isNotEmpty().allMatch(n -> n.matches("(pk|fk|uq|ck|ex)_[a-z0-9_]+"));
    assertThat(indexes).isNotEmpty().allMatch(n -> n.matches("(pk|uq|ex|idx)_[a-z0-9_]+"));
    assertThat(constraints)
        .contains(
            "pk_activity_log",
            "ck_activity_log_role",
            "ck_activity_log_outcome",
            "pk_feature_flag",
            "ck_backup_log_outcome");
    assertThat(indexes)
        .contains(
            "idx_activity_time",
            "idx_activity_role_time",
            "idx_activity_user_time",
            "idx_app_error_log_occurred_at",
            "idx_change_history_entity");
  }

  @Test
  void activityLogMatchesThePinnedDefinition() throws SQLException {
    Map<String, String> columns = new LinkedHashMap<>();
    try (Connection c = TestDatabase.admin();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT column_name, data_type, is_nullable, character_maximum_length"
                    + " FROM information_schema.columns WHERE table_schema = 'ums'"
                    + " AND table_name = 'activity_log' ORDER BY ordinal_position")) {
      while (rs.next()) {
        String length = rs.getString(4) == null ? "" : "(" + rs.getString(4) + ")";
        columns.put(rs.getString(1), rs.getString(2) + length + " " + rs.getString(3));
      }
    }
    assertThat(columns)
        .containsExactly(
            Map.entry("id", "bigint NO"),
            Map.entry("occurred_at", "timestamp with time zone NO"),
            Map.entry("user_id", "bigint YES"),
            Map.entry("username", "character varying(64) YES"),
            Map.entry("role", "character varying(16) NO"),
            Map.entry("session_id", "uuid YES"),
            Map.entry("action", "character varying(64) NO"),
            Map.entry("entity_type", "character varying(64) YES"),
            Map.entry("entity_id", "character varying(64) YES"),
            Map.entry("outcome", "character varying(8) NO"),
            Map.entry("details", "jsonb YES"),
            Map.entry("trace_id", "uuid YES"));
  }

  @Test
  void activityLogRoleCheckAcceptsSystemAndAnonymousOnly() throws SQLException {
    try (Connection c = TestDatabase.as("ums_app");
        Statement st = c.createStatement()) {
      for (String role : List.of("STUDENT", "FACULTY", "ADMIN", "DEV", "SYSTEM", "ANONYMOUS")) {
        st.execute(
            "INSERT INTO activity_log (role, action, outcome) VALUES ('"
                + role
                + "', 'IT', 'SUCCESS')");
      }
      assertThatThrownBy(
              () ->
                  st.execute(
                      "INSERT INTO activity_log (role, action, outcome) VALUES ('ROOT', 'IT', 'SUCCESS')"))
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"))
          .hasMessageContaining("ck_activity_log_role");
      st.execute("DELETE FROM activity_log WHERE action = 'IT'");
    }
  }

  @Test
  void seedsTheFiveFeatureFlagsEnabled() throws SQLException {
    assertThat(rows("SELECT key || '=' || enabled FROM ums.feature_flag"))
        .containsExactlyInAnyOrder(
            "REGISTRATION=true",
            "MARKS_ENTRY=true",
            "RESULT_PUBLISH=true",
            "NOTICES=true",
            "PRIVACY_EXPORT=true");
  }

  @Test
  void installsBtreeGist() throws SQLException {
    assertThat(rows("SELECT extname FROM pg_extension")).contains("btree_gist");
  }

  private static List<String> rows(String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection c = TestDatabase.admin();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        values.add(rs.getString(1));
      }
    }
    return values;
  }
}
