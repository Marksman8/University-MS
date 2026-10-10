package edu.ums.core.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.platform.config.Environment;
import edu.ums.platform.db.MigrationRunner;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

/** ums_listener may LISTEN and read activity_log, and nothing else. */
class ListenerRoleIT {

  private static final String PERMISSION_DENIED = "42501";

  @BeforeAll
  static void migrate() {
    new MigrationRunner(CoreDbSupport.owner(), Environment.TEST).migrate();
  }

  @Test
  void canListenAndSelectActivityLog() throws SQLException {
    try (Connection listener = CoreDbSupport.as("ums_listener");
        Connection writer = CoreDbSupport.as("ums_app")) {
      try (Statement st = listener.createStatement()) {
        st.execute("LISTEN ums_activity");
      }
      long id;
      try (Statement st = writer.createStatement();
          ResultSet rs =
              st.executeQuery(
                  "INSERT INTO activity_log (role, action, outcome)"
                      + " VALUES ('SYSTEM', 'IT_LISTENER', 'SUCCESS') RETURNING id")) {
        rs.next();
        id = rs.getLong(1);
      }

      PGNotification[] notifications = listener.unwrap(PGConnection.class).getNotifications(5_000);
      assertThat(notifications).isNotNull().hasSize(1);
      long notified = Long.parseLong(notifications[0].getParameter());
      assertThat(notified).isEqualTo(id);

      try (Statement st = listener.createStatement();
          ResultSet rs =
              st.executeQuery("SELECT action FROM activity_log WHERE id = " + notified)) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("IT_LISTENER");
      }

      try (Statement st = writer.createStatement()) {
        st.execute("DELETE FROM activity_log WHERE action = 'IT_LISTENER'");
      }
    }
  }

  @Test
  void cannotSelectAnyOtherTable() throws SQLException {
    List<String> others = otherTables();
    assertThat(others)
        .contains(
            "app_error_log",
            "feature_flag",
            "backup_log",
            "change_history",
            "flyway_schema_history");
    for (String table : others) {
      assertDenied("SELECT count(*) FROM ums." + table);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "INSERT INTO activity_log (role, action, outcome) VALUES ('SYSTEM', 'X', 'SUCCESS')",
        "UPDATE activity_log SET action = 'X'",
        "DELETE FROM activity_log"
      })
  void cannotWriteActivityLog(String sql) throws SQLException {
    assertDenied(sql);
  }

  @Test
  void cannotCreateTables() throws SQLException {
    assertDenied("CREATE TABLE ums.it_forbidden (id int)");
  }

  private static List<String> otherTables() throws SQLException {
    List<String> tables = new ArrayList<>();
    try (Connection c = CoreDbSupport.admin();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'ums'"
                    + " AND tablename <> 'activity_log' ORDER BY 1")) {
      while (rs.next()) {
        tables.add(rs.getString(1));
      }
    }
    return tables;
  }

  private static void assertDenied(String sql) throws SQLException {
    try (Connection c = CoreDbSupport.as("ums_listener");
        Statement st = c.createStatement()) {
      assertThatThrownBy(() -> st.execute(sql))
          .as(sql)
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(PERMISSION_DENIED));
    }
  }
}
