package edu.ums.proto.term.repository;

import edu.ums.proto.term.domain.TermRow;
import edu.ums.proto.term.domain.TermState;
import java.util.List;
import java.util.Optional;

/** Prototype persistence of {@code term} and its set-based transition effects. */
public interface TermRepository {

  /** The current term (highest id). */
  Optional<TermRow> current();

  /** The current term, row-locked {@code FOR SHARE} (registration). */
  Optional<TermRow> currentForShare();

  /** Compare-and-set of the state; STALE_DATA when it moved meanwhile. */
  void changeState(long termId, TermState from, TermState to, long version);

  /** Inserts a DRAFT sheet for every section of the term that has none. */
  int createDraftSheets(long termId);

  /** Codes of sections whose sheet is not APPROVED, ordered by code. */
  List<String> unapprovedSections(long termId);

  /** APPROVED sheets → PUBLISHED. */
  int publishSheets(long termId);

  /** ENROLLED → COMPLETED for the term. */
  int completeEnrolments(long termId);

  /** Computes {@code term_result} for every student of the term in one INSERT … SELECT. */
  int computeTermResults(long termId);
}
