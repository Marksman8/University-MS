package edu.ums.proto.facade;

import edu.ums.api.AdminApi;
import edu.ums.api.Role;
import edu.ums.api.proto.ActivityRow;
import edu.ums.api.proto.MarkSheetSummary;
import edu.ums.api.proto.TermView;
import edu.ums.api.proto.UserRow;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.auth.service.AuthService;
import edu.ums.proto.marks.service.MarksService;
import edu.ums.proto.shared.Actor;
import edu.ums.proto.term.domain.TermState;
import edu.ums.proto.term.service.TermService;
import java.util.List;
import java.util.Objects;

/** Admin facade bound to one signed-in admin. */
public final class AdminFacade implements AdminApi {

  private final Actor admin;
  private final TermService terms;
  private final MarksService marks;
  private final AuthService auth;
  private final ActivityRecorder activity;

  /** Creates the facade for {@code admin}. */
  public AdminFacade(
      Actor admin,
      TermService terms,
      MarksService marks,
      AuthService auth,
      ActivityRecorder activity) {
    this.admin = Objects.requireNonNull(admin, "admin");
    this.terms = Objects.requireNonNull(terms, "terms");
    this.marks = Objects.requireNonNull(marks, "marks");
    this.auth = Objects.requireNonNull(auth, "auth");
    this.activity = Objects.requireNonNull(activity, "activity");
  }

  @Override
  public TermView currentTerm() {
    return terms.currentView();
  }

  @Override
  public TermView startTeaching() {
    return terms.advance(admin, TermState.IN_PROGRESS);
  }

  @Override
  public TermView startGrading() {
    return terms.advance(admin, TermState.GRADING);
  }

  @Override
  public TermView publishResults() {
    return terms.advance(admin, TermState.RESULTS_PUBLISHED);
  }

  @Override
  public List<MarkSheetSummary> markSheets() {
    return marks.summaries();
  }

  @Override
  public void approveSheet(long sectionId) {
    marks.approve(admin, sectionId);
  }

  @Override
  public void returnSheet(long sectionId) {
    marks.returnToFaculty(admin, sectionId);
  }

  @Override
  public List<UserRow> users() {
    return auth.users();
  }

  @Override
  public String createUser(Role role, String username, String fullName) {
    return auth.createUser(admin, role, username, fullName);
  }

  @Override
  public List<ActivityRow> activity(long afterId) {
    return activity.feed(afterId);
  }
}
