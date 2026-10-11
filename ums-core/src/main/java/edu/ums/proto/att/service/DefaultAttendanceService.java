package edu.ums.proto.att.service;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.AttendanceSummary;
import edu.ums.api.proto.RosterEntry;
import edu.ums.platform.error.AccessDeniedException;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.RuleViolationException;
import edu.ums.proto.att.domain.AttendancePercent;
import edu.ums.proto.att.repository.AttendanceRepository;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.shared.Actor;
import edu.ums.proto.term.repository.TermRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Attendance for own sections; percent per course for students. */
// Complexity: time O(b) per record of b marks (one batch); O(k) per summary for k courses;
// space O(k)
public final class DefaultAttendanceService implements AttendanceService {

  private final AttendanceRepository attendance;
  private final TermRepository terms;
  private final ActivityRecorder activity;
  private final Clock clock;

  /** Creates the service. */
  public DefaultAttendanceService(
      AttendanceRepository attendance,
      TermRepository terms,
      ActivityRecorder activity,
      Clock clock) {
    this.attendance = Objects.requireNonNull(attendance, "attendance");
    this.terms = Objects.requireNonNull(terms, "terms");
    this.activity = Objects.requireNonNull(activity, "activity");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public List<RosterEntry> roster(Actor faculty, long sectionId) {
    requireOwn(faculty, sectionId);
    return attendance.roster(sectionId);
  }

  @Override
  public void record(Actor faculty, long sectionId, LocalDate date, List<AttendanceMark> marks) {
    requireOwn(faculty, sectionId);
    if (date == null || date.isAfter(LocalDate.now(clock))) {
      throw new RuleViolationException(
          ErrorCode.INVALID_SESSION_DATE, Map.of("date", String.valueOf(date)));
    }
    long sessionId = attendance.upsertSession(sectionId, date);
    int rows = attendance.upsertAttendance(sessionId, sectionId, List.copyOf(marks));
    activity.success(
        faculty,
        "ATTENDANCE_RECORDED",
        "class_session",
        sessionId,
        Map.of("date", date.toString(), "rows", rows));
  }

  @Override
  public List<AttendanceSummary> summary(Actor student) {
    long termId =
        terms.current().orElseThrow(() -> new NotFoundException(Map.of("entity", "term"))).id();
    return attendance.counts(student.userId(), termId).stream()
        .map(
            c ->
                new AttendanceSummary(
                    c.courseCode(),
                    c.courseTitle(),
                    c.present(),
                    c.absent(),
                    c.excused(),
                    AttendancePercent.of(c.present(), c.absent())))
        .toList();
  }

  private void requireOwn(Actor faculty, long sectionId) {
    if (!attendance.ownsSection(faculty.userId(), sectionId)) {
      throw new AccessDeniedException(Map.of());
    }
  }
}
