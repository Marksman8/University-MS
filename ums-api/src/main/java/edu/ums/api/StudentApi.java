package edu.ums.api;

import edu.ums.api.annotation.Writes;
import edu.ums.api.proto.AttendanceSummary;
import edu.ums.api.proto.ResultsView;
import edu.ums.api.proto.SectionView;
import java.util.List;

/**
 * Student facade: registration, timetable, attendance, results, transcript, notices and privacy
 * requests for the signed-in student only. Operations are added by the feature phases (P1–P6); the
 * methods below are the prototype slice and always act for the session's own student.
 */
public interface StudentApi {

  /** Sections of the current term, with seats left and whether the student holds one. */
  List<SectionView> openSections();

  /** Registers for a section (WINDOW_CLOSED, ALREADY_ENROLLED, PREREQ_NOT_MET, ...). */
  @Writes
  void register(long sectionId);

  /** Drops a section while registration is open. */
  @Writes
  void drop(long sectionId);

  /** The student's sections in the current term, ordered by day and time. */
  List<SectionView> timetable();

  /** Attendance per course in the current term. */
  List<AttendanceSummary> attendance();

  /** Results of the latest published term with SGPA and CGPA. */
  ResultsView results();
}
