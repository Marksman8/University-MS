package edu.ums.proto.marks.domain;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.state.Guard;
import edu.ums.platform.state.GuardResult;
import edu.ums.platform.state.StateMachine;
import java.util.Map;

/**
 * Mark sheet lifecycle (prototype).
 *
 * <pre>
 *   DRAFT     -> SUBMITTED  guard: no missing mark (MARKS_INCOMPLETE)
 *   SUBMITTED -> APPROVED   (admin; results computed by the service)
 *   SUBMITTED -> DRAFT      (admin return)
 *   APPROVED  -> PUBLISHED  (term publish, set-based)
 * </pre>
 */
public final class SheetLifecycle {

  /**
   * Transition context.
   *
   * @param missingMarks enrolled students × components without a value or absent flag
   */
  public record Context(long missingMarks) {}

  /** Every enrolled student needs a value or absent for every component. */
  public static final Guard<Context> COMPLETE =
      c ->
          c.missingMarks() == 0
              ? GuardResult.pass()
              : GuardResult.fail(ErrorCode.MARKS_INCOMPLETE, Map.of("missing", c.missingMarks()));

  /** The machine. */
  public static final StateMachine<SheetState, Context> MACHINE =
      StateMachine.<SheetState, Context>builder(SheetState.class)
          .transition(SheetState.DRAFT, SheetState.SUBMITTED, COMPLETE)
          .transition(SheetState.SUBMITTED, SheetState.APPROVED)
          .transition(SheetState.SUBMITTED, SheetState.DRAFT)
          .transition(SheetState.APPROVED, SheetState.PUBLISHED)
          .build();

  private SheetLifecycle() {}
}
