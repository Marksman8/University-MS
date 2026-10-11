package edu.ums.app.proto;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.api.Session;
import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.testkit.ConcurrencyHarness;
import edu.ums.testkit.ConcurrencyResult;
import edu.ums.testkit.TestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 20 students race for the last seat of a capacity-1 section through the student facade. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtoLastSeatIT {

  private static final int THREADS = 20;

  private ProtoFixture fixture;

  @BeforeAll
  void start() throws Exception {
    fixture = new ProtoFixture();
    TestDatabase.exec(
        "INSERT INTO ums.app_user (username, password_hash, role, full_name)"
            + " SELECT 'load.' || g, (SELECT password_hash FROM ums.app_user"
            + " WHERE username = 'stu.anu'), 'STUDENT', 'Load ' || g"
            + " FROM generate_series(1, 20) g",
        "INSERT INTO ums.student (user_id, programme_id, roll_no)"
            + " SELECT u.id, (SELECT id FROM ums.programme LIMIT 1), 'LOAD' || u.id"
            + " FROM ums.app_user u WHERE u.username LIKE 'load.%'",
        "UPDATE ums.section s SET capacity = 1 FROM ums.course c, ums.term t"
            + " WHERE c.id = s.course_id AND t.id = s.term_id AND c.code = 'CS502'"
            + " AND t.state = 'REGISTRATION_OPEN'");
  }

  @AfterAll
  void stop() {
    fixture.close();
  }

  @Test
  void lastSeatGoesToExactlyOneStudent() throws Exception {
    List<Session> sessions = new ArrayList<>();
    for (int i = 1; i <= THREADS; i++) {
      sessions.add(fixture.login("load." + i));
    }
    long section = ProtoFixture.section(sessions.get(0).student().openSections(), "CS502");
    AtomicInteger next = new AtomicInteger();
    ConcurrencyResult<String> result =
        ConcurrencyHarness.run(
            THREADS,
            () -> {
              Session mine = sessions.get(next.getAndIncrement());
              try {
                mine.student().register(section);
                return "OK";
              } catch (UmsApiException e) {
                return e.code().name();
              }
            });
    assertThat(result.failures()).isEmpty();
    assertThat(result.outcomes()).filteredOn("OK"::equals).hasSize(1);
    assertThat(result.outcomes())
        .filteredOn(ErrorCode.SECTION_FULL.name()::equals)
        .hasSize(THREADS - 1);
    try (Connection c = TestDatabase.admin();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT s.enrolled_count, (SELECT count(*) FROM ums.enrolment e"
                    + " WHERE e.section_id = s.id AND e.status = 'ENROLLED')"
                    + " FROM ums.section s WHERE s.id = "
                    + section)) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
      assertThat(rs.getInt(2)).isEqualTo(1);
    }
    sessions.forEach(Session::close);
  }
}
