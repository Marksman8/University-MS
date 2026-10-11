package edu.ums.proto.marks.repository;

import edu.ums.api.proto.ResultRow;
import edu.ums.platform.db.BaseRepository;
import edu.ums.platform.db.CursorCodec;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.SqlErrorMapper;
import java.util.List;
import java.util.Optional;

/** JDBC {@link ResultsRepository}. */
// Complexity: time O(log n + k) per read for the student's k results; space O(k)
public final class JdbcResultsRepository extends BaseRepository implements ResultsRepository {

  /** Creates the repository. */
  public JdbcResultsRepository(
      TransactionRunner transactions, SqlErrorMapper errors, CursorCodec cursors) {
    super(transactions, errors, cursors);
  }

  @Override
  public Optional<TermGpa> latestPublished(long studentId) {
    return queryOptional(
        "SELECT t.id, t.name, tr.sgpa, tr.cgpa, tr.credits_earned FROM term_result tr"
            + " JOIN term t ON t.id = tr.term_id"
            + " WHERE tr.student_id = ? AND t.state = 'RESULTS_PUBLISHED'"
            + " ORDER BY t.id DESC LIMIT 1",
        ps -> ps.setLong(1, studentId),
        rs ->
            new TermGpa(
                rs.getLong(1),
                rs.getString(2),
                rs.getBigDecimal(3),
                rs.getBigDecimal(4),
                rs.getInt(5)));
  }

  @Override
  public List<ResultRow> results(long studentId, long termId) {
    return queryList(
        "SELECT c.code, c.title, c.credits, r.total_pct, r.grade, r.grade_point"
            + " FROM enrolment e JOIN result r ON r.enrolment_id = e.id"
            + " JOIN course c ON c.id = e.course_id"
            + " WHERE e.student_id = ? AND e.term_id = ? AND e.status = 'COMPLETED'"
            + " ORDER BY c.code LIMIT 50",
        ps -> {
          ps.setLong(1, studentId);
          ps.setLong(2, termId);
        },
        rs ->
            new ResultRow(
                rs.getString(1),
                rs.getString(2),
                rs.getInt(3),
                rs.getBigDecimal(4),
                rs.getString(5),
                rs.getBigDecimal(6)),
        50);
  }
}
