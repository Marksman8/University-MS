package edu.ums.proto.term.domain;

/** Side effects of term transitions, implemented by the service layer (Strategy). */
public interface TermEffects {

  /** Creates a DRAFT mark sheet for every section of the term. */
  void createDraftSheets(long termId);

  /** Publishes sheets, completes enrolments and computes term results set-based. */
  void publishResults(long termId);
}
