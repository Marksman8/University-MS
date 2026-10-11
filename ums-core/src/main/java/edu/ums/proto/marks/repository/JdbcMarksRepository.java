package edu.ums.proto.marks.repository;

import edu.ums.api.proto.ComponentView;
import edu.ums.api.proto.MarkEntry;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.RosterEntry;
import edu.ums.api.proto.SectionView;
import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.StatementBinder;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.proto.marks.domain.GradeCalculator;
import edu.ums.proto.marks.domain.SheetRow;
import edu.ums.proto.marks.domain.SheetState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** JDBC {@link MarksRepository}. */
// Complexity: time O(log n + r) per section read, r = enrolled × components (one join, no N+1);
// O(b) per batch of b marks; space O(r), r <= capacity × components
public final class JdbcMarksRepository extends BaseRepository implements MarksRepository {

  private static final int ROW_LIMIT = 5_000;

  private static final String SHEET =
      "SELECT m.id, s.id AS section_id, s.code AS section_code, c.code AS course_code,"
          + " s.faculty_id, m.state, m.version FROM mark_sheet m"
          + " JOIN section s ON s.id = m.section_id JOIN course c ON c.id = s.course_id"
          + " WHERE s.id = ?";

  /** Creates the repository. */
  public JdbcMarksRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public List<SectionView> sectionsOf(long facultyId, long termId) {
    return queryList(
        "SELECT s.id, c.code, c.title, s.code, c.credits, s.day, s.start_min, s.end_min, s.room,"
            + " s.capacity, s.enrolled_count, f.full_name, m.state FROM section s"
            + " JOIN course c ON c.id = s.course_id JOIN app_user f ON f.id = s.faculty_id"
            + " LEFT JOIN mark_sheet m ON m.section_id = s.id"
            + " WHERE s.faculty_id = ? AND s.term_id = ? ORDER BY c.code, s.code LIMIT 200",
        ps -> {
          ps.setLong(1, facultyId);
          ps.setLong(2, termId);
        },
        rs ->
            new SectionView(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getInt(5),
                rs.getInt(6),
                rs.getInt(7),
                rs.getInt(8),
                rs.getString(9),
                rs.getInt(10),
                rs.getInt(11),
                rs.getString(12),
                true,
                rs.getString(13)),
        200);
  }

  @Override
  public Optional<SheetRow> lockSheet(long sectionId) {
    return queryOptional(
        SHEET + " FOR UPDATE OF m", ps -> ps.setLong(1, sectionId), this::sheetRow);
  }

  @Override
  public Optional<SheetRow> sheet(long sectionId) {
    return queryOptional(SHEET, ps -> ps.setLong(1, sectionId), this::sheetRow);
  }

  @Override
  public List<ComponentView> components(long sectionId) {
    return queryList(
        "SELECT id, name, max_marks, weight_pct FROM assessment_component WHERE section_id = ?"
            + " ORDER BY id",
        ps -> ps.setLong(1, sectionId),
        rs ->
            new ComponentView(
                rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4)),
        50);
  }

  @Override
  public List<RosterEntry> roster(long sectionId) {
    return queryList(
        "SELECT e.id, st.roll_no, u.full_name FROM enrolment e"
            + " JOIN student st ON st.user_id = e.student_id JOIN app_user u ON u.id = e.student_id"
            + " WHERE e.section_id = ? AND e.status IN ('ENROLLED', 'COMPLETED')"
            + " ORDER BY st.roll_no",
        ps -> ps.setLong(1, sectionId),
        rs -> new RosterEntry(rs.getLong(1), rs.getString(2), rs.getString(3)),
        ROW_LIMIT);
  }

  @Override
  public List<StoredMark> marks(long sectionId) {
    return queryList(
        "SELECT mk.enrolment_id, mk.component_id, mk.value, mk.absent FROM mark mk"
            + " JOIN enrolment e ON e.id = mk.enrolment_id"
            + " WHERE e.section_id = ? AND e.status IN ('ENROLLED', 'COMPLETED')",
        ps -> ps.setLong(1, sectionId),
        rs -> new StoredMark(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3), rs.getBoolean(4)),
        ROW_LIMIT);
  }

  @Override
  public int upsertMarks(long sectionId, List<MarkEntry> entries) {
    return batch(
        "INSERT INTO mark (enrolment_id, component_id, value, absent)"
            + " SELECT e.id, ?, ?, ? FROM enrolment e"
            + " WHERE e.id = ? AND e.section_id = ? AND e.status = 'ENROLLED'"
            + " ON CONFLICT (enrolment_id, component_id)"
            + " DO UPDATE SET value = EXCLUDED.value, absent = EXCLUDED.absent",
        entries,
        (ps, entry) -> {
          ps.setLong(1, entry.componentId());
          if (entry.absent() || entry.value() == null) {
            ps.setNull(2, Types.NUMERIC);
          } else {
            ps.setBigDecimal(2, entry.value());
          }
          ps.setBoolean(3, entry.absent());
          ps.setLong(4, entry.enrolmentId());
          ps.setLong(5, sectionId);
        });
  }

  @Override
  public long missingMarks(long sectionId) {
    return queryOne(
        "SELECT count(*) FROM enrolment e JOIN assessment_component ac ON ac.section_id = e.section_id"
            + " LEFT JOIN mark mk ON mk.enrolment_id = e.id AND mk.component_id = ac.id"
            + " WHERE e.section_id = ? AND e.status = 'ENROLLED'"
            + " AND (mk.enrolment_id IS NULL OR (mk.value IS NULL AND NOT mk.absent))",
        ps -> ps.setLong(1, sectionId),
        rs -> rs.getLong(1));
  }

  @Override
  public void changeState(long sheetId, SheetState from, SheetState to, long version) {
    casState("mark_sheet", sheetId, from.name(), to.name(), version);
  }

  @Override
  public Map<java.math.BigDecimal, GradeCalculator.Grade> gradeScale() {
    Map<java.math.BigDecimal, GradeCalculator.Grade> scale = new TreeMap<>();
    queryList(
            "SELECT grade, min_percent, grade_point FROM grade_scale",
            StatementBinder.NONE,
            rs ->
                Map.entry(
                    rs.getBigDecimal(2),
                    new GradeCalculator.Grade(rs.getString(1), rs.getBigDecimal(3))),
            50)
        .forEach(e -> scale.put(e.getKey(), e.getValue()));
    return scale;
  }

  @Override
  public void upsertResults(List<ResultRow> results) {
    batch(
        "INSERT INTO result (enrolment_id, total_pct, grade, grade_point) VALUES (?, ?, ?, ?)"
            + " ON CONFLICT (enrolment_id) DO UPDATE SET total_pct = EXCLUDED.total_pct,"
            + " grade = EXCLUDED.grade, grade_point = EXCLUDED.grade_point",
        results,
        (ps, r) -> {
          ps.setLong(1, r.enrolmentId());
          ps.setBigDecimal(2, r.totalPct());
          ps.setString(3, r.grade());
          ps.setBigDecimal(4, r.gradePoint());
        });
  }

  @Override
  public List<MarkSheetSummary> summaries(long termId) {
    return queryList(
        "SELECT s.id, s.code, c.code, f.full_name, m.state FROM section s"
            + " JOIN course c ON c.id = s.course_id JOIN app_user f ON f.id = s.faculty_id"
            + " JOIN mark_sheet m ON m.section_id = s.id WHERE s.term_id = ?"
            + " ORDER BY c.code, s.code LIMIT 200",
        ps -> ps.setLong(1, termId),
        rs ->
            new MarkSheetSummary(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
        200);
  }

  private SheetRow sheetRow(ResultSet rs) throws SQLException {
    return new SheetRow(
        rs.getLong("id"),
        rs.getLong("section_id"),
        rs.getString("section_code"),
        rs.getString("course_code"),
        rs.getLong("faculty_id"),
        SheetState.valueOf(rs.getString("state")),
        rs.getLong("version"));
  }
}
