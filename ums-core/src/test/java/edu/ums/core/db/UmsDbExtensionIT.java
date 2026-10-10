package edu.ums.core.db;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.testkit.TestDatabase;
import edu.ums.testkit.UmsDbExtension;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/** UmsDbExtension: migrated schema, empty application tables before each test, seed data kept. */
@ExtendWith(UmsDbExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UmsDbExtensionIT {

  @Test
  @Order(1)
  void firstTestWritesARow() throws SQLException {
    assertThat(insertActivity()).isEqualTo(1);
    assertThat(count("activity_log")).isEqualTo(1);
  }

  @Test
  @Order(2)
  void nextTestStartsEmptyWithIdentitiesResetAndSeedDataKept() throws SQLException {
    assertThat(count("activity_log")).isZero();
    assertThat(count("feature_flag")).isEqualTo(5);
    assertThat(count("flyway_schema_history")).isPositive();
    assertThat(insertActivity()).isEqualTo(1);
  }

  private static long insertActivity() throws SQLException {
    try (Connection c = TestDatabase.as("ums_app");
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "INSERT INTO activity_log (role, action, outcome)"
                    + " VALUES ('SYSTEM', 'IT_EXTENSION', 'SUCCESS') RETURNING id")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static long count(String table) throws SQLException {
    try (Connection c = TestDatabase.admin();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM ums." + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
