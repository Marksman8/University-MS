package edu.ums.api;

import edu.ums.api.annotation.Writes;
import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkSheetView;
import edu.ums.api.proto.RosterEntry;
import edu.ums.api.proto.SectionView;
import java.time.LocalDate;
import java.util.List;

/**
 * Faculty facade: own sections, class sessions and attendance, mark entry and submission, faculty
 * reports and notices. Operations are added by the feature phases (P1–P6); the methods below are
 * the prototype slice and only ever touch the signed-in faculty member's own sections.
 */
public interface FacultyApi {

  /** Own sections in the current term. */
  List<SectionView> mySections();

  /** Enrolled students of an own section. */
  List<RosterEntry> roster(long sectionId);

  /** Records (or re-records) attendance of an own section for a date that is not in the future. */
  @Writes
  void recordAttendance(long sectionId, LocalDate date, List<AttendanceMark> marks);

  /** The mark sheet of an own section. */
  MarkSheetView markSheet(long sectionId);

  /** Saves marks while the sheet is DRAFT (NOT_EDITABLE, MARK_OUT_OF_RANGE). */
  @Writes
  void saveMarks(long sectionId, List<MarkEntry> entries);

  /** Submits a complete DRAFT sheet (MARKS_INCOMPLETE). */
  @Writes
  void submitSheet(long sectionId);
}
