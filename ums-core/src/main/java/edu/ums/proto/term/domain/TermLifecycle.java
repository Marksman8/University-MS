package edu.ums.proto.term.domain;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.state.Guard;
import edu.ums.platform.state.GuardResult;
import edu.ums.platform.state.StateMachine;
import java.util.List;
import java.util.Map;

/**
 * Term lifecycle (prototype).
 *
 * <pre>
 *   REGISTRATION_OPEN -> IN_PROGRESS        action: DRAFT mark sheet per section
 *   IN_PROGRESS       -> GRADING
 *   GRADING           -> RESULTS_PUBLISHED  guard: every sheet APPROVED; action: publish
 * </pre>
 */
public final class TermLifecycle {

  /**
   * Transition context.
   *
   * @param unapprovedSections codes of sections whose sheet is not APPROVED (bounded by sections)
   */
  public record Transition(long termId, List<String> unapprovedSections, TermEffects effects) {

    /** Copies the list. */
    public Transition {
      unapprovedSections = List.copyOf(unapprovedSections);
    }
  }

  /** Every sheet must be APPROVED before publishing; lists the sections that are not. */
  public static final Guard<Transition> ALL_SHEETS_APPROVED =
      t ->
          t.unapprovedSections().isEmpty()
              ? GuardResult.pass()
              : GuardResult.fail(
                  ErrorCode.GUARD_FAILED,
                  Map.of("sections", String.join(", ", t.unapprovedSections())));

  /** The machine. */
  public static final StateMachine<TermState, Transition> MACHINE =
      StateMachine.<TermState, Transition>builder(TermState.class)
          .transition(
              TermState.REGISTRATION_OPEN,
              TermState.IN_PROGRESS,
              Guard.always(),
              t -> t.effects().createDraftSheets(t.termId()))
          .transition(TermState.IN_PROGRESS, TermState.GRADING)
          .transition(
              TermState.GRADING,
              TermState.RESULTS_PUBLISHED,
              ALL_SHEETS_APPROVED,
              t -> t.effects().publishResults(t.termId()))
          .build();

  private TermLifecycle() {}
}
