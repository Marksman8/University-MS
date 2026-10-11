package edu.ums.app.proto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.AdminApi;
import edu.ums.api.FacultyApi;
import edu.ums.api.Session;
import edu.ums.api.StudentApi;
import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.api.proto.ActivityRow;
import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.AttendanceStatus;
import edu.ums.api.proto.AttendanceSummary;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkRow;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.MarkSheetView;
import edu.ums.api.proto.QueryResult;
import edu.ums.api.proto.ResultsView;
import edu.ums.api.proto.SectionView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * §1 demo steps 1–6 through the facades. Hand-calculated expectations (credits × grade point):
 *
 * <pre>
 *   stu.anu : CS502 90 O 10 (4cr), CS503 84 A+ 9 (3cr), CS504 77 A 8 (3cr)
 *             SGPA = (40 + 27 + 24) / 10 = 9.10; history CS501 A+ 9 (4cr)
 *             CGPA = (91 + 36) / 14 = 9.0714 → 9.07; credits earned 10
 *   stu.binu: CS502 70 A 8 (4cr), CS503 47 C 5 (3cr)
 *             SGPA = (32 + 15) / 7 = 6.714 → 6.71; history CS501 A 8 (4cr)
 *             CGPA = (47 + 32) / 11 = 7.1818 → 7.18; credits earned 7
 * </pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtoTermCycleIT {

  private ProtoFixture fixture;

  @BeforeAll
  void start() {
    fixture = new ProtoFixture();
  }

  @AfterAll
  void stop() {
    fixture.close();
  }

  @Test
  void fullTermCycle() {
    Map<String, Long> ids = registration();
    try (Session admin = fixture.login("admin")) {
      assertThat(admin.admin().startTeaching().state()).isEqualTo("IN_PROGRESS");
      assertThat(admin.admin().markSheets()).hasSize(4).allMatch(s -> s.state().equals("DRAFT"));
    }
    teaching(ids);
    grading();
    results();
    devSeesEverything();
  }

  private Map<String, Long> registration() {
    try (Session anu = fixture.login("stu.anu");
        Session binu = fixture.login("stu.binu");
        Session chitra = fixture.login("stu.chitra")) {
      StudentApi a = anu.student();
      List<SectionView> sections = a.openSections();
      long cs501 = ProtoFixture.section(sections, "CS501");
      long cs502 = ProtoFixture.section(sections, "CS502");
      long cs503 = ProtoFixture.section(sections, "CS503");
      long cs504 = ProtoFixture.section(sections, "CS504");
      a.register(cs502);
      a.register(cs503);
      a.register(cs504);
      assertCode(() -> a.register(cs501), ErrorCode.TIMETABLE_CLASH);
      assertThat(a.timetable())
          .extracting(SectionView::courseCode)
          .containsExactly("CS502", "CS503", "CS504");
      binu.student().register(cs502);
      binu.student().register(cs503);
      assertCode(() -> chitra.student().register(cs503), ErrorCode.SECTION_FULL);
      chitra.student().register(cs502);
      assertCode(chitra::admin, ErrorCode.ACCESS_DENIED);
      try (Session esha = fixture.login("stu.esha")) {
        assertCode(() -> esha.student().register(cs504), ErrorCode.PREREQ_NOT_MET);
      }
      return Map.of("CS501", cs501, "CS502", cs502, "CS503", cs503, "CS504", cs504);
    }
  }

  private void teaching(Map<String, Long> ids) {
    try (Session menon = fixture.login("fac.menon");
        Session rao = fixture.login("fac.rao")) {
      FacultyApi m = menon.faculty();
      assertThat(m.mySections())
          .extracting(SectionView::courseCode)
          .containsExactly("CS502", "CS504");
      long cs502 = ids.get("CS502");
      List<AttendanceMark> att = new ArrayList<>();
      m.roster(cs502)
          .forEach(
              r ->
                  att.add(
                      new AttendanceMark(
                          r.enrolmentId(),
                          r.studentName().startsWith("Binu")
                              ? AttendanceStatus.ABSENT
                              : AttendanceStatus.PRESENT)));
      m.recordAttendance(cs502, LocalDate.now().minusDays(1), att);
      assertCode(
          () -> m.recordAttendance(cs502, LocalDate.now().plusDays(1), att),
          ErrorCode.INVALID_SESSION_DATE);
      assertCode(() -> m.markSheet(ids.get("CS503")), ErrorCode.ACCESS_DENIED);
      enter(m, cs502, Map.of("Anu", "36/54", "Binu", "28/42", "Chitra", "20/30"));
      assertCode(() -> m.submitSheet(ids.get("CS504")), ErrorCode.MARKS_INCOMPLETE);
      enter(m, ids.get("CS504"), Map.of("Anu", "32/45"));
      FacultyApi r = rao.faculty();
      assertCode(
          () -> r.saveMarks(ids.get("CS503"), List.of(entry(r, ids.get("CS503"), 0, "41"))),
          ErrorCode.MARK_OUT_OF_RANGE);
      enter(r, ids.get("CS503"), Map.of("Anu", "34/50", "Binu", "22/25"));
      r.submitSheet(ids.get("CS501")); // empty section: nothing missing
      for (long id : List.of(cs502, ids.get("CS504"))) {
        m.submitSheet(id);
      }
      r.submitSheet(ids.get("CS503"));
      assertCode(
          () -> r.saveMarks(ids.get("CS503"), List.of(entry(r, ids.get("CS503"), 0, "1"))),
          ErrorCode.NOT_EDITABLE);
    }
  }

  private void grading() {
    try (Session session = fixture.login("admin")) {
      AdminApi admin = session.admin();
      admin.startGrading();
      List<MarkSheetSummary> sheets = admin.markSheets();
      admin.approveSheet(sheets.get(0).sectionId());
      assertCode(admin::publishResults, ErrorCode.GUARD_FAILED);
      for (MarkSheetSummary s : sheets.subList(1, sheets.size())) {
        admin.approveSheet(s.sectionId());
      }
      assertThat(admin.publishResults().state()).isEqualTo("RESULTS_PUBLISHED");
    }
  }

  private void results() {
    try (Session anu = fixture.login("stu.anu");
        Session binu = fixture.login("stu.binu")) {
      ResultsView a = anu.student().results();
      assertThat(a.results())
          .extracting(r -> r.courseCode() + " " + r.totalPct().toPlainString() + " " + r.grade())
          .containsExactly("CS502 90.00 O", "CS503 84.00 A+", "CS504 77.00 A");
      assertThat(a.sgpa()).isEqualByComparingTo("9.10");
      assertThat(a.cgpa()).isEqualByComparingTo("9.07");
      assertThat(a.creditsEarned()).isEqualTo(10);
      ResultsView b = binu.student().results();
      assertThat(b.sgpa()).isEqualByComparingTo("6.71");
      assertThat(b.cgpa()).isEqualByComparingTo("7.18");
      assertThat(b.creditsEarned()).isEqualTo(7);
      assertThat(anu.student().attendance())
          .filteredOn(s -> s.courseCode().equals("CS502"))
          .extracting(AttendanceSummary::percent)
          .containsExactly(new BigDecimal("100.00"));
      assertThat(binu.student().attendance())
          .filteredOn(s -> s.courseCode().equals("CS502"))
          .extracting(AttendanceSummary::percent)
          .containsExactly(new BigDecimal("0.00"));
    }
  }

  private void devSeesEverything() {
    try (Session dev = fixture.login("dev")) {
      List<ActivityRow> feed = dev.dev().activity(0);
      assertThat(feed)
          .extracting(ActivityRow::action)
          .contains(
              "LOGIN",
              "LOGOUT",
              "REGISTER",
              "register",
              "ACCESS_DENIED",
              "TERM_IN_PROGRESS",
              "ATTENDANCE_RECORDED",
              "MARKS_SAVED",
              "SHEET_SUBMITTED",
              "TERM_GRADING",
              "SHEET_APPROVED",
              "TERM_RESULTS_PUBLISHED");
      assertThat(feed)
          .filteredOn(r -> r.action().equals("register"))
          .allMatch(r -> r.outcome().equals("FAILURE"));
      QueryResult ok = dev.dev().runQuery("-- results\nSELECT count(*) FROM result");
      assertThat(ok.error()).isNull();
      assertThat(ok.rows()).containsExactly(List.of("11")); // 6 this term + 5 history
      QueryResult denied = dev.dev().runQuery("SELECT password_hash FROM app_user");
      assertThat(denied.sqlState()).isEqualTo("42501");
      assertThat(dev.dev().runQuery("DELETE FROM term").sqlState()).isEqualTo("GUARD");
      assertThat(dev.dev().health().status()).isEqualTo("UP");
    }
  }

  /** Enters "internal/final" marks for students whose name starts with the key. */
  private static void enter(FacultyApi api, long sectionId, Map<String, String> marks) {
    MarkSheetView sheet = api.markSheet(sectionId);
    List<MarkEntry> entries = new ArrayList<>();
    for (MarkRow row : sheet.rows()) {
      marks.forEach(
          (prefix, pair) -> {
            if (row.studentName().startsWith(prefix)) {
              String[] parts = pair.split("/");
              for (int i = 0; i < 2; i++) {
                entries.add(
                    new MarkEntry(
                        row.enrolmentId(),
                        sheet.components().get(i).id(),
                        new BigDecimal(parts[i]),
                        false));
              }
            }
          });
    }
    api.saveMarks(sectionId, entries);
  }

  private static MarkEntry entry(FacultyApi api, long sectionId, int component, String value) {
    MarkSheetView sheet = api.markSheet(sectionId);
    return new MarkEntry(
        sheet.rows().get(0).enrolmentId(),
        sheet.components().get(component).id(),
        new BigDecimal(value),
        false);
  }

  private static void assertCode(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(UmsApiException.class, e -> assertThat(e.code()).isEqualTo(code));
  }
}
