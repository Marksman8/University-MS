package edu.ums.proto.marks.service;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.proto.ComponentView;
import edu.ums.api.proto.MarkCell;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkRow;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.MarkSheetView;
import edu.ums.api.proto.RosterEntry;
import edu.ums.api.proto.SectionView;
import edu.ums.platform.error.AccessDeniedException;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.RuleViolationException;
import edu.ums.platform.error.ValidationException;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.marks.domain.GradeCalculator;
import edu.ums.proto.marks.domain.SheetLifecycle;
import edu.ums.proto.marks.domain.SheetRow;
import edu.ums.proto.marks.domain.SheetState;
import edu.ums.proto.marks.repository.MarksRepository;
import edu.ums.proto.marks.repository.MarksRepository.ResultRow;
import edu.ums.proto.marks.repository.MarksRepository.StoredMark;
import edu.ums.proto.shared.Actor;
import edu.ums.proto.term.repository.TermRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mark entry (own DRAFT sheets only), submission, approval with grading, and return. */
// Complexity: time O(r) per sheet read/approve, r = enrolled × components (one query each, no
// N+1); O(b) per save of b entries with an O(1) HashMap component lookup; space O(r)
public final class DefaultMarksService implements MarksService {

  private final MarksRepository marks;
  private final TermRepository terms;
  private final ActivityRecorder activity;

  /** Creates the service. */
  public DefaultMarksService(
      MarksRepository marks, TermRepository terms, ActivityRecorder activity) {
    this.marks = Objects.requireNonNull(marks, "marks");
    this.terms = Objects.requireNonNull(terms, "terms");
    this.activity = Objects.requireNonNull(activity, "activity");
  }

  @Override
  public List<SectionView> mySections(Actor faculty) {
    return marks.sectionsOf(faculty.userId(), currentTermId());
  }

  @Override
  public MarkSheetView sheet(Actor faculty, long sectionId) {
    SheetRow sheet = own(faculty, marks.sheet(sectionId));
    List<ComponentView> components = marks.components(sectionId);
    Map<Long, Map<Long, StoredMark>> byEnrolment = new HashMap<>();
    for (StoredMark m : marks.marks(sectionId)) {
      byEnrolment.computeIfAbsent(m.enrolmentId(), k -> new HashMap<>()).put(m.componentId(), m);
    }
    List<MarkRow> rows = new ArrayList<>();
    for (RosterEntry student : marks.roster(sectionId)) {
      Map<Long, StoredMark> mine = byEnrolment.getOrDefault(student.enrolmentId(), Map.of());
      List<MarkCell> cells = new ArrayList<>(components.size());
      for (ComponentView c : components) {
        StoredMark m = mine.get(c.id());
        cells.add(new MarkCell(c.id(), m == null ? null : m.value(), m != null && m.absent()));
      }
      rows.add(new MarkRow(student.enrolmentId(), student.rollNo(), student.studentName(), cells));
    }
    return new MarkSheetView(
        sectionId,
        sheet.sectionCode(),
        sheet.courseCode(),
        sheet.state().name(),
        components,
        List.copyOf(rows));
  }

  @Override
  public void save(Actor faculty, long sectionId, List<MarkEntry> entries) {
    SheetRow sheet = own(faculty, marks.lockSheet(sectionId));
    if (sheet.state() != SheetState.DRAFT) {
      throw new RuleViolationException(ErrorCode.NOT_EDITABLE, Map.of("state", sheet.state()));
    }
    Map<Long, BigDecimal> maxById = new HashMap<>();
    marks.components(sectionId).forEach(c -> maxById.put(c.id(), c.maxMarks()));
    for (MarkEntry entry : entries) {
      BigDecimal max = maxById.get(entry.componentId());
      if (max == null) {
        throw new ValidationException(Map.of("field", "componentId"));
      }
      if (!entry.absent() && entry.value() != null && outOfRange(entry.value(), max)) {
        throw new RuleViolationException(
            ErrorCode.MARK_OUT_OF_RANGE, Map.of("value", entry.value(), "max", max));
      }
    }
    int saved = marks.upsertMarks(sectionId, List.copyOf(entries));
    activity.success(faculty, "MARKS_SAVED", "mark_sheet", sheet.sheetId(), Map.of("rows", saved));
  }

  @Override
  public void submit(Actor faculty, long sectionId) {
    SheetRow sheet = own(faculty, marks.lockSheet(sectionId));
    transition(sheet, SheetState.SUBMITTED, marks.missingMarks(sectionId));
    activity.success(faculty, "SHEET_SUBMITTED", "mark_sheet", sheet.sheetId(), label(sheet));
  }

  @Override
  public void approve(Actor admin, long sectionId) {
    SheetRow sheet = found(marks.lockSheet(sectionId));
    SheetLifecycle.MACHINE.fire(sheet.state(), SheetState.APPROVED, new SheetLifecycle.Context(0));
    marks.upsertResults(computeResults(sectionId));
    marks.changeState(sheet.sheetId(), sheet.state(), SheetState.APPROVED, sheet.version());
    activity.success(admin, "SHEET_APPROVED", "mark_sheet", sheet.sheetId(), label(sheet));
  }

  @Override
  public void returnToFaculty(Actor admin, long sectionId) {
    SheetRow sheet = found(marks.lockSheet(sectionId));
    transition(sheet, SheetState.DRAFT, 0);
    activity.success(admin, "SHEET_RETURNED", "mark_sheet", sheet.sheetId(), label(sheet));
  }

  @Override
  public List<MarkSheetSummary> summaries() {
    return marks.summaries(currentTermId());
  }

  private List<ResultRow> computeResults(long sectionId) {
    GradeCalculator calculator = new GradeCalculator(marks.gradeScale());
    List<GradeCalculator.Component> components =
        marks.components(sectionId).stream()
            .map(c -> new GradeCalculator.Component(c.id(), c.maxMarks(), c.weightPct()))
            .toList();
    Map<Long, Map<Long, GradeCalculator.Mark>> byEnrolment = new HashMap<>();
    for (StoredMark m : marks.marks(sectionId)) {
      byEnrolment
          .computeIfAbsent(m.enrolmentId(), k -> new HashMap<>())
          .put(m.componentId(), new GradeCalculator.Mark(m.componentId(), m.value(), m.absent()));
    }
    List<ResultRow> results = new ArrayList<>();
    for (RosterEntry student : marks.roster(sectionId)) {
      BigDecimal total =
          calculator.total(components, byEnrolment.getOrDefault(student.enrolmentId(), Map.of()));
      GradeCalculator.Grade grade = calculator.grade(total);
      results.add(new ResultRow(student.enrolmentId(), total, grade.letter(), grade.point()));
    }
    return results;
  }

  private void transition(SheetRow sheet, SheetState target, long missing) {
    SheetLifecycle.MACHINE.fire(sheet.state(), target, new SheetLifecycle.Context(missing));
    marks.changeState(sheet.sheetId(), sheet.state(), target, sheet.version());
  }

  private static boolean outOfRange(BigDecimal value, BigDecimal max) {
    return value.signum() < 0 || value.compareTo(max) > 0;
  }

  private static SheetRow own(Actor faculty, java.util.Optional<SheetRow> sheet) {
    SheetRow row = found(sheet);
    if (row.facultyId() != faculty.userId()) {
      throw new AccessDeniedException(Map.of());
    }
    return row;
  }

  private static SheetRow found(java.util.Optional<SheetRow> sheet) {
    return sheet.orElseThrow(() -> new NotFoundException(Map.of("entity", "mark_sheet")));
  }

  private static Map<String, Object> label(SheetRow sheet) {
    return Map.of("section", sheet.courseCode() + "-" + sheet.sectionCode());
  }

  private long currentTermId() {
    return terms.current().orElseThrow(() -> new NotFoundException(Map.of("entity", "term"))).id();
  }
}
