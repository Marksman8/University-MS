package edu.ums.proto.marks.repository;

import edu.ums.api.proto.ResultRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Prototype reads of published results. Runs in the caller's transaction. */
public interface ResultsRepository {

  /** A student's GPA row for a term. */
  record TermGpa(long termId, String termName, BigDecimal sgpa, BigDecimal cgpa, int credits) {}

  /** The student's GPA row for the latest published term, if any. */
  Optional<TermGpa> latestPublished(long studentId);

  /** The student's results in a term ordered by course code. */
  List<ResultRow> results(long studentId, long termId);
}
