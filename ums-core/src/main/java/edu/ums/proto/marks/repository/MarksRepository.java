package edu.ums.proto.marks.repository;

import edu.ums.api.proto.ComponentView;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.RosterEntry;
import edu.ums.api.proto.SectionView;
import edu.ums.proto.marks.domain.GradeCalculator;
import edu.ums.proto.marks.domain.SheetRow;
import edu.ums.proto.marks.domain.SheetState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Prototype persistence of mark sheets, marks and results. Runs in the caller's transaction. */
public interface MarksRepository {

  /** A stored mark for one enrolment and component. */
  record StoredMark(long enrolmentId, long componentId, BigDecimal value, boolean absent) {}

  /** A computed result to store. */
  record ResultRow(long enrolmentId, BigDecimal totalPct, String grade, BigDecimal gradePoint) {}

  /** The faculty member's sections in the term. */
  List<SectionView> sectionsOf(long facultyId, long termId);

  /** The sheet of a section, row-locked {@code FOR UPDATE}. */
  Optional<SheetRow> lockSheet(long sectionId);

  /** The sheet of a section (no lock). */
  Optional<SheetRow> sheet(long sectionId);

  /** Components of a section ordered by id. */
  List<ComponentView> components(long sectionId);

  /** ENROLLED students of a section ordered by roll number. */
  List<RosterEntry> roster(long sectionId);

  /** Every stored mark of the section's ENROLLED students. */
  List<StoredMark> marks(long sectionId);

  /** Batch upsert; entries for students not ENROLLED in the section are ignored. */
  int upsertMarks(long sectionId, List<MarkEntry> entries);

  /** Enrolled × components minus complete marks. */
  long missingMarks(long sectionId);

  /** Compare-and-set of the sheet state. */
  void changeState(long sheetId, SheetState from, SheetState to, long version);

  /** The grade scale keyed by minimum percent. */
  Map<BigDecimal, GradeCalculator.Grade> gradeScale();

  /** Batch upsert of results. */
  void upsertResults(List<ResultRow> results);

  /** Sheets of the term's sections. */
  List<MarkSheetSummary> summaries(long termId);
}
