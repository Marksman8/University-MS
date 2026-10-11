package edu.ums.api;

import edu.ums.api.annotation.Writes;
import edu.ums.api.proto.ActivityRow;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.TermView;
import edu.ums.api.proto.UserRow;
import java.util.List;

/**
 * Admin facade: users, organisation structure, terms and offerings, overrides, mark-sheet approval,
 * result publishing, notices, reports, feature flags and privacy approvals. Operations are added by
 * the feature phases (P1–P6); the methods below are the prototype slice.
 */
public interface AdminApi {

  /** The current term. */
  TermView currentTerm();

  /** REGISTRATION_OPEN → IN_PROGRESS; creates a DRAFT mark sheet per section. */
  @Writes
  TermView startTeaching();

  /** IN_PROGRESS → GRADING. */
  @Writes
  TermView startGrading();

  /** GRADING → RESULTS_PUBLISHED once every sheet is APPROVED (GUARD_FAILED lists the rest). */
  @Writes
  TermView publishResults();

  /** Mark sheets of the current term. */
  List<MarkSheetSummary> markSheets();

  /** SUBMITTED → APPROVED; computes each student's total and grade. */
  @Writes
  void approveSheet(long sectionId);

  /** SUBMITTED → DRAFT so the faculty member can correct it. */
  @Writes
  void returnSheet(long sectionId);

  /** Up to 200 users ordered by username. */
  List<UserRow> users();

  /** Creates a user and returns a one-time temporary password (never stored in clear). */
  @Writes
  String createUser(Role role, String username, String fullName);

  /** Activity entries newer than {@code afterId} (≤ 0: the latest 200), oldest first. */
  List<ActivityRow> activity(long afterId);
}
