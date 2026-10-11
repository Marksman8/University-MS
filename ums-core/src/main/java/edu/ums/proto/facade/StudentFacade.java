package edu.ums.proto.facade;

import edu.ums.api.StudentApi;
import edu.ums.api.proto.AttendanceSummary;
import edu.ums.api.proto.ResultsView;
import edu.ums.api.proto.SectionView;
import edu.ums.proto.att.service.AttendanceService;
import edu.ums.proto.marks.service.ResultsService;
import edu.ums.proto.reg.service.RegistrationService;
import edu.ums.proto.shared.Actor;
import java.util.List;
import java.util.Objects;

/** Student facade bound to one signed-in student; every call acts for that student only. */
public final class StudentFacade implements StudentApi {

  private final Actor student;
  private final RegistrationService registration;
  private final AttendanceService attendance;
  private final ResultsService results;

  /** Creates the facade for {@code student}. */
  public StudentFacade(
      Actor student,
      RegistrationService registration,
      AttendanceService attendance,
      ResultsService results) {
    this.student = Objects.requireNonNull(student, "student");
    this.registration = Objects.requireNonNull(registration, "registration");
    this.attendance = Objects.requireNonNull(attendance, "attendance");
    this.results = Objects.requireNonNull(results, "results");
  }

  @Override
  public List<SectionView> openSections() {
    return registration.sections(student);
  }

  @Override
  public void register(long sectionId) {
    registration.register(student, sectionId);
  }

  @Override
  public void drop(long sectionId) {
    registration.drop(student, sectionId);
  }

  @Override
  public List<SectionView> timetable() {
    return registration.timetable(student);
  }

  @Override
  public List<AttendanceSummary> attendance() {
    return attendance.summary(student);
  }

  @Override
  public ResultsView results() {
    return results.forStudent(student);
  }
}
