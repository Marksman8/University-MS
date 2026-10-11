package edu.ums.proto.reg.repository;

import edu.ums.api.proto.SectionView;
import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.proto.reg.domain.SectionSlot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC {@link RegistrationRepository}. */
// Complexity: time O(log n + s) per list (idx_section_term), s <= 200 sections; O(log n + k) per
// check over the student's k enrolments (idx_enrolment_student_term); space O(s)
public final class JdbcRegistrationRepository extends BaseRepository
    implements RegistrationRepository {

  /** Section columns for {@link SectionView}; parameter 1 is the student id. */
  static final String SECTION_VIEW =
      "SELECT s.id, c.code AS course_code, c.title, s.code AS section_code, c.credits, s.day,"
          + " s.start_min, s.end_min, s.room, s.capacity, s.enrolled_count,"
          + " f.full_name AS faculty_name, m.state AS sheet_state,"
          + " EXISTS (SELECT 1 FROM enrolment e WHERE e.section_id = s.id AND e.student_id = ?"
          + "   AND e.status <> 'DROPPED') AS mine"
          + " FROM section s JOIN course c ON c.id = s.course_id"
          + " JOIN app_user f ON f.id = s.faculty_id"
          + " LEFT JOIN mark_sheet m ON m.section_id = s.id";

  private static final int LIMIT = 200;

  /** Creates the repository. */
  public JdbcRegistrationRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public List<SectionView> sections(long termId, long studentId) {
    return queryList(
        SECTION_VIEW + " WHERE s.term_id = ? ORDER BY c.code, s.code LIMIT 200",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
        },
        JdbcRegistrationRepository::view,
        LIMIT);
  }

  @Override
  public List<SectionView> timetable(long termId, long studentId) {
    return queryList(
        SECTION_VIEW
            + " WHERE s.term_id = ? AND EXISTS (SELECT 1 FROM enrolment e WHERE e.section_id = s.id"
            + " AND e.student_id = ? AND e.status <> 'DROPPED')"
            + " ORDER BY s.day, s.start_min LIMIT 200",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
          ps.setLong(3, studentId);
        },
        JdbcRegistrationRepository::view,
        LIMIT);
  }

  @Override
  public Optional<SectionSlot> slot(long sectionId) {
    return queryOptional(
        "SELECT s.id, s.term_id, s.course_id, c.code || '-' || s.code AS label, c.credits, s.day,"
            + " s.start_min, s.end_min FROM section s JOIN course c ON c.id = s.course_id"
            + " WHERE s.id = ?",
        ps -> ps.setLong(1, sectionId),
        rs ->
            new SectionSlot(
                rs.getLong("id"),
                rs.getLong("term_id"),
                rs.getLong("course_id"),
                rs.getString("label"),
                rs.getInt("credits"),
                rs.getInt("day"),
                rs.getInt("start_min"),
                rs.getInt("end_min")));
  }

  @Override
  public boolean lockStudent(long studentId) {
    return queryOptional(
            "SELECT user_id FROM student WHERE user_id = ? FOR UPDATE",
            ps -> ps.setLong(1, studentId),
            rs -> rs.getLong(1))
        .isPresent();
  }

  @Override
  public boolean alreadyEnrolled(long studentId, long termId, long courseId) {
    return queryOne(
        "SELECT EXISTS (SELECT 1 FROM enrolment WHERE student_id = ? AND term_id = ?"
            + " AND course_id = ? AND status = 'ENROLLED')",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
          ps.setLong(3, courseId);
        },
        rs -> rs.getBoolean(1));
  }

  @Override
  public List<String> unmetPrerequisites(long studentId, long courseId) {
    return queryList(
        "SELECT pc.code FROM course_prereq p JOIN course pc ON pc.id = p.prereq_id"
            + " WHERE p.course_id = ? AND NOT EXISTS (SELECT 1 FROM enrolment e"
            + " JOIN result r ON r.enrolment_id = e.id"
            + " WHERE e.student_id = ? AND e.course_id = p.prereq_id"
            + " AND e.status = 'COMPLETED' AND r.grade <> 'F') ORDER BY pc.code LIMIT 50",
        ps -> {
          ps.setLong(1, courseId);
          ps.setLong(2, studentId);
        },
        rs -> rs.getString(1),
        50);
  }

  @Override
  public int enrolledCredits(long studentId, long termId) {
    return queryOne(
        "SELECT coalesce(sum(c.credits), 0) FROM enrolment e JOIN course c ON c.id = e.course_id"
            + " WHERE e.student_id = ? AND e.term_id = ? AND e.status = 'ENROLLED'",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
        },
        rs -> rs.getInt(1));
  }

  @Override
  public Optional<String> clash(long studentId, SectionSlot slot) {
    return queryList(
            "SELECT c.code || '-' || s.code FROM enrolment e"
                + " JOIN section s ON s.id = e.section_id JOIN course c ON c.id = s.course_id"
                + " WHERE e.student_id = ? AND e.term_id = ? AND e.status = 'ENROLLED'"
                + " AND s.day = ? AND s.start_min < ? AND ? < s.end_min LIMIT 1",
            ps -> {
              ps.setLong(1, studentId);
              ps.setLong(2, slot.termId());
              ps.setInt(3, slot.day());
              ps.setInt(4, slot.endMin());
              ps.setInt(5, slot.startMin());
            },
            rs -> rs.getString(1),
            1)
        .stream()
        .findFirst();
  }

  @Override
  public boolean takeSeat(long sectionId) {
    return update(
            "UPDATE section SET enrolled_count = enrolled_count + 1, version = version + 1"
                + " WHERE id = ? AND enrolled_count < capacity",
            ps -> ps.setLong(1, sectionId))
        == 1;
  }

  @Override
  public void releaseSeat(long sectionId) {
    updateExactlyOne(
        "UPDATE section SET enrolled_count = enrolled_count - 1, version = version + 1"
            + " WHERE id = ? AND enrolled_count > 0",
        ps -> ps.setLong(1, sectionId),
        Map.of("entity", "section"));
  }

  @Override
  public long insertEnrolment(long studentId, SectionSlot slot) {
    return queryOne(
        "INSERT INTO enrolment (student_id, section_id, term_id, course_id) VALUES (?, ?, ?, ?)"
            + " RETURNING id",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, slot.id());
          ps.setLong(3, slot.termId());
          ps.setLong(4, slot.courseId());
        },
        rs -> rs.getLong(1));
  }

  @Override
  public Optional<Long> liveEnrolment(long studentId, long sectionId) {
    return queryOptional(
        "SELECT id FROM enrolment WHERE student_id = ? AND section_id = ? AND status = 'ENROLLED'",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, sectionId);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public void dropEnrolment(long enrolmentId) {
    updateExactlyOne(
        "UPDATE enrolment SET status = 'DROPPED', version = version + 1"
            + " WHERE id = ? AND status = 'ENROLLED'",
        ps -> ps.setLong(1, enrolmentId),
        Map.of("entity", "enrolment"));
  }

  static SectionView view(ResultSet rs) throws SQLException {
    return new SectionView(
        rs.getLong("id"),
        rs.getString("course_code"),
        rs.getString("title"),
        rs.getString("section_code"),
        rs.getInt("credits"),
        rs.getInt("day"),
        rs.getInt("start_min"),
        rs.getInt("end_min"),
        rs.getString("room"),
        rs.getInt("capacity"),
        rs.getInt("enrolled_count"),
        rs.getString("faculty_name"),
        rs.getBoolean("mine"),
        rs.getString("sheet_state"));
  }
}
