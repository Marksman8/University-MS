package edu.ums.proto.dev.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsoleGuardTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "UPDATE app_user SET active = false",
        "DROP TABLE term",
        "delete from enrolment",
        "SELECT 1; DROP TABLE term",
        "select 1; select 2",
        "  -- SELECT\nINSERT INTO term VALUES (1)",
        "SELECTX 1",
        "SELECT 'unterminated",
        "SELECT $$x$$",
        "",
        "   "
      })
  void rejects(String sql) {
    assertThat(ConsoleGuard.check(sql)).isInstanceOf(ConsoleGuard.Rejected.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SELECT 1",
        "select * from term",
        "-- latest terms\nSELECT * FROM term",
        "  /* block */  WITH t AS (SELECT 1) SELECT * FROM t",
        "SELECT ';' AS semi",
        "SELECT 1; -- trailing comment",
        "SELECT 1;   "
      })
  void accepts(String sql) {
    assertThat(ConsoleGuard.check(sql)).isInstanceOf(ConsoleGuard.Allowed.class);
  }

  @Test
  void selectAfterACommentIsAcceptedAndTrailingSemicolonDropped() {
    assertThat(ConsoleGuard.check("-- who is here\n  SELECT username FROM app_user;"))
        .isEqualTo(new ConsoleGuard.Allowed("SELECT username FROM app_user"));
  }

  @Test
  void rejectsOverlongInput() {
    assertThat(ConsoleGuard.check("SELECT " + "1".repeat(ConsoleGuard.MAX_LENGTH)))
        .isInstanceOf(ConsoleGuard.Rejected.class);
  }
}
