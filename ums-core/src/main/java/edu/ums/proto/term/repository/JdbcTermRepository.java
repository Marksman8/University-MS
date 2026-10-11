package edu.ums.proto.term.repository;

import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.StatementBinder;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.proto.term.domain.TermRow;
import edu.ums.proto.term.domain.TermState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** JDBC {@link TermRepository}. */
// Complexity: time O(log n) per term read/CAS; transition effects are single set-based statements
// O(s + e) for s sections and e enrolments of the term; space O(s) for the unapproved list
public final class JdbcTermRepository extends BaseRepository implements TermRepository {

  private static final String CURRENT =
      "SELECT id, name, state, version FROM term ORDER BY id DESC LIMIT 1";

  /**
   * SGPA over this term's results, CGPA over every completed result of a published term (this one
   * included; all attempts count), credits earned excluding F.
   */
  static final String TERM_RESULTS =
      "INSERT INTO term_result (student_id, term_id, sgpa, cgpa, credits_earned)"
          + " SELECT e.student_id, ?,"
          + " round(sum(CASE WHEN e.term_id = ? THEN c.credits * r.grade_point END)"
          + "   / nullif(sum(CASE WHEN e.term_id = ? THEN c.credits END), 0), 2),"
          + " round(sum(c.credits * r.grade_point) / nullif(sum(c.credits), 0), 2),"
          + " coalesce(sum(CASE WHEN e.term_id = ? AND r.grade <> 'F' THEN c.credits END), 0)"
          + " FROM enrolment e"
          + " JOIN result r ON r.enrolment_id = e.id"
          + " JOIN course c ON c.id = e.course_id"
          + " JOIN term t ON t.id = e.term_id"
          + " WHERE e.status = 'COMPLETED' AND (t.state = 'RESULTS_PUBLISHED' OR t.id = ?)"
          + " GROUP BY e.student_id"
          + " HAVING count(*) FILTER (WHERE e.term_id = ?) > 0"
          + " ON CONFLICT (student_id, term_id) DO UPDATE"
          + " SET sgpa = EXCLUDED.sgpa, cgpa = EXCLUDED.cgpa,"
          + " credits_earned = EXCLUDED.credits_earned";

  /** Creates the repository. */
  public JdbcTermRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public Optional<TermRow> current() {
    return queryOptional(CURRENT, StatementBinder.NONE, JdbcTermRepository::map);
  }

  @Override
  public Optional<TermRow> currentForShare() {
    return queryOptional(CURRENT + " FOR SHARE", StatementBinder.NONE, JdbcTermRepository::map);
  }

  @Override
  public void changeState(long termId, TermState from, TermState to, long version) {
    casState("term", termId, from.name(), to.name(), version);
  }

  @Override
  public int createDraftSheets(long termId) {
    return update(
        "INSERT INTO mark_sheet (section_id) SELECT id FROM section WHERE term_id = ?"
            + " ON CONFLICT (section_id) DO NOTHING",
        ps -> ps.setLong(1, termId));
  }

  @Override
  public List<String> unapprovedSections(long termId) {
    return queryList(
        "SELECT c.code || '-' || s.code FROM section s JOIN course c ON c.id = s.course_id"
            + " LEFT JOIN mark_sheet m ON m.section_id = s.id"
            + " WHERE s.term_id = ? AND (m.state IS NULL OR m.state <> 'APPROVED')"
            + " ORDER BY 1 LIMIT 200",
        ps -> ps.setLong(1, termId),
        rs -> rs.getString(1),
        200);
  }

  @Override
  public int publishSheets(long termId) {
    return update(
        "UPDATE mark_sheet m SET state = 'PUBLISHED', version = m.version + 1 FROM section s"
            + " WHERE s.id = m.section_id AND s.term_id = ? AND m.state = 'APPROVED'",
        ps -> ps.setLong(1, termId));
  }

  @Override
  public int completeEnrolments(long termId) {
    return update(
        "UPDATE enrolment SET status = 'COMPLETED', version = version + 1"
            + " WHERE term_id = ? AND status = 'ENROLLED'",
        ps -> ps.setLong(1, termId));
  }

  @Override
  public int computeTermResults(long termId) {
    return update(
        TERM_RESULTS,
        ps -> {
          for (int i = 1; i <= 6; i++) {
            ps.setLong(i, termId);
          }
        });
  }

  private static TermRow map(ResultSet rs) throws SQLException {
    return new TermRow(
        rs.getLong("id"),
        rs.getString("name"),
        TermState.valueOf(rs.getString("state")),
        rs.getLong("version"));
  }
}
