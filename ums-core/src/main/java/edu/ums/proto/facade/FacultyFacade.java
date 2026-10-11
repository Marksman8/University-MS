package edu.ums.proto.facade;

import edu.ums.api.FacultyApi;
import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkSheetView;
import edu.ums.api.proto.RosterEntry;
import edu.ums.api.proto.SectionView;
import edu.ums.proto.att.service.AttendanceService;
import edu.ums.proto.marks.service.MarksService;
import edu.ums.proto.shared.Actor;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Faculty facade bound to one signed-in faculty member; only own sections are reachable. */
public final class FacultyFacade implements FacultyApi {

  private final Actor faculty;
  private final AttendanceService attendance;
  private final MarksService marks;

  /** Creates the facade for {@code faculty}. */
  public FacultyFacade(Actor faculty, AttendanceService attendance, MarksService marks) {
    this.faculty = Objects.requireNonNull(faculty, "faculty");
    this.attendance = Objects.requireNonNull(attendance, "attendance");
    this.marks = Objects.requireNonNull(marks, "marks");
  }

  @Override
  public List<SectionView> mySections() {
    return marks.mySections(faculty);
  }

  @Override
  public List<RosterEntry> roster(long sectionId) {
    return attendance.roster(faculty, sectionId);
  }

  @Override
  public void recordAttendance(long sectionId, LocalDate date, List<AttendanceMark> entries) {
    attendance.record(faculty, sectionId, date, entries);
  }

  @Override
  public MarkSheetView markSheet(long sectionId) {
    return marks.sheet(faculty, sectionId);
  }

  @Override
  public void saveMarks(long sectionId, List<MarkEntry> entries) {
    marks.save(faculty, sectionId, entries);
  }

  @Override
  public void submitSheet(long sectionId) {
    marks.submit(faculty, sectionId);
  }
}
