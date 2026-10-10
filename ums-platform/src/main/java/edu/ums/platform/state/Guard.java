package edu.ums.platform.state;

/** Decides whether a transition may happen for a given context (Strategy). */
@FunctionalInterface
public interface Guard<C> {

  /** A guard that always passes. */
  static <C> Guard<C> always() {
    return context -> GuardResult.pass();
  }

  /**
   * Evaluates the guard. Must not change state; side effects belong in an {@link Action}.
   *
   * @return {@link GuardResult#pass()} or a {@link GuardResult.Fail}
   */
  GuardResult check(C context);
}
