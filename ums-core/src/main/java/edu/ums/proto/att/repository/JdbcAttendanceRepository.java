package edu.ums.proto.att.repository;

import edu.ums.api.proto.AttendanceMark;
import edu.ums.api.proto.RosterEntry;
import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import java.time.LocalDate;
import java.util.List;

/** JDBC {@link AttendanceRepository}. */
// Complexity: time O(b) per batch of b marks; O(log n + a) per summary over the student's a
// attendance rows (set-based GROUP BY); space O(k) for k courses
public final class JdbcAttendanceRepository extends BaseRepository implements AttendanceRepository {

  /** Creates the repository. */
  public JdbcAttendanceRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public boolean ownsSection(long facultyId, long sectionId) {
    return queryOne(
        "SELECT EXISTS (SELECT 1 FROM section WHERE id = ? AND faculty_id = ?)",
        ps -> {
          ps.setLong(1, sectionId);
          ps.setLong(2, facultyId);
        },
        rs -> rs.getBoolean(1));
  }

  @Override
  public List<RosterEntry> roster(long sectionId) {
    return queryList(
        "SELECT e.id, st.roll_no, u.full_name FROM enrolment e"
            + " JOIN student st ON st.user_id = e.student_id JOIN app_user u ON u.id = e.student_id"
            + " WHERE e.section_id = ? AND e.status = 'ENROLLED' ORDER BY st.roll_no",
        ps -> ps.setLong(1, sectionId),
        rs -> new RosterEntry(rs.getLong(1), rs.getString(2), rs.getString(3)),
        1_000);
  }

  @Override
  public long upsertSession(long sectionId, LocalDate date) {
    return queryOne(
        "INSERT INTO class_session (section_id, session_date) VALUES (?, ?)"
            + " ON CONFLICT (section_id, session_date) DO UPDATE SET recorded_at = now()"
            + " RETURNING id",
        ps -> {
          ps.setLong(1, sectionId);
          ps.setObject(2, date);
        },
        rs -> rs.getLong(1));
  }

  @Override
  public int upsertAttendance(long sessionId, long sectionId, List<AttendanceMark> marks) {
    return batch(
        "INSERT INTO attendance (session_id, enrolment_id, status)"
            + " SELECT ?, e.id, ? FROM enrolment e"
            + " WHERE e.id = ? AND e.section_id = ? AND e.status = 'ENROLLED'"
            + " ON CONFLICT (session_id, enrolment_id) DO UPDATE SET status = EXCLUDED.status",
        marks,
        (ps, mark) -> {
          ps.setLong(1, sessionId);
          ps.setString(2, mark.status().name());
          ps.setLong(3, mark.enrolmentId());
          ps.setLong(4, sectionId);
        });
  }

  @Override
  public List<Counts> counts(long studentId, long termId) {
    return queryList(
        "SELECT c.code, c.title,"
            + " count(a.*) FILTER (WHERE a.status = 'PRESENT'),"
            + " count(a.*) FILTER (WHERE a.status = 'ABSENT'),"
            + " count(a.*) FILTER (WHERE a.status = 'EXCUSED')"
            + " FROM enrolment e JOIN course c ON c.id = e.course_id"
            + " LEFT JOIN attendance a ON a.enrolment_id = e.id"
            + " WHERE e.student_id = ? AND e.term_id = ? AND e.status <> 'DROPPED'"
            + " GROUP BY c.code, c.title ORDER BY c.code LIMIT 50",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
        },
        rs ->
            new Counts(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)),
        50);
  }
}
