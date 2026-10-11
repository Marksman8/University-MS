package edu.ums.proto.term.service;

import edu.ums.api.proto.TermView;
import edu.ums.platform.error.NotFoundException;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.shared.Actor;
import edu.ums.proto.term.domain.TermEffects;
import edu.ums.proto.term.domain.TermLifecycle;
import edu.ums.proto.term.domain.TermRow;
import edu.ums.proto.term.domain.TermState;
import edu.ums.proto.term.repository.TermRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Term transitions: guard + action via {@link TermLifecycle}, then compare-and-set. */
// Complexity: time O(s + e) per transition (set-based effects over the term's sections and
// enrolments); space O(s) for the unapproved-section list, s <= 200
public final class DefaultTermService implements TermService {

  private final TermRepository terms;
  private final ActivityRecorder activity;
  private final TermEffects effects;

  /** Creates the service. */
  public DefaultTermService(TermRepository terms, ActivityRecorder activity) {
    this.terms = Objects.requireNonNull(terms, "terms");
    this.activity = Objects.requireNonNull(activity, "activity");
    this.effects =
        new TermEffects() {
          @Override
          public void createDraftSheets(long termId) {
            terms.createDraftSheets(termId);
          }

          @Override
          public void publishResults(long termId) {
            terms.publishSheets(termId);
            terms.completeEnrolments(termId);
            terms.computeTermResults(termId);
          }
        };
  }

  @Override
  public TermRow current() {
    return terms.current().orElseThrow(() -> new NotFoundException(Map.of("entity", "term")));
  }

  @Override
  public TermView currentView() {
    TermRow term = current();
    return new TermView(term.id(), term.name(), term.state().name());
  }

  @Override
  public TermView advance(Actor admin, TermState target) {
    TermRow term = current();
    List<String> unapproved =
        target == TermState.RESULTS_PUBLISHED ? terms.unapprovedSections(term.id()) : List.of();
    TermLifecycle.MACHINE.fire(
        term.state(), target, new TermLifecycle.Transition(term.id(), unapproved, effects));
    terms.changeState(term.id(), term.state(), target, term.version());
    activity.success(
        admin, "TERM_" + target.name(), "term", term.id(), Map.of("from", term.state()));
    return new TermView(term.id(), term.name(), target.name());
  }
}
