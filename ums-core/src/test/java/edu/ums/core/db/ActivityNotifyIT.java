package edu.ums.core.db;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.testkit.TestDatabase;
import edu.ums.testkit.UmsDbExtension;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

/** trg_activity_notify sends NOTIFY ums_activity with the new row id on every insert. */
@ExtendWith(UmsDbExtension.class)
class ActivityNotifyIT {

  @Test
  void insertIntoActivityLogNotifiesListenersWithTheRowId() throws SQLException {
    try (Connection listener = TestDatabase.as("ums_app");
        Connection writer = TestDatabase.as("ums_app")) {
      try (Statement st = listener.createStatement()) {
        st.execute("LISTEN ums_activity");
      }

      long id;
      try (Statement st = writer.createStatement();
          ResultSet rs =
              st.executeQuery(
                  "INSERT INTO activity_log (role, action, outcome)"
                      + " VALUES ('ANONYMOUS', 'IT_NOTIFY', 'FAILURE') RETURNING id")) {
        rs.next();
        id = rs.getLong(1);
      }

      PGNotification[] notifications = listener.unwrap(PGConnection.class).getNotifications(5_000);

      assertThat(notifications).isNotNull().hasSize(1);
      assertThat(notifications[0].getName()).isEqualTo("ums_activity");
      assertThat(notifications[0].getParameter()).isEqualTo(Long.toString(id));

      try (Statement st = writer.createStatement()) {
        st.execute("DELETE FROM activity_log WHERE action = 'IT_NOTIFY'");
      }
    }
  }
}
