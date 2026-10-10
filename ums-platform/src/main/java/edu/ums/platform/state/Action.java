package edu.ums.platform.state;

/** Work done once a transition's guard has passed. Exceptions propagate to the caller. */
@FunctionalInterface
public interface Action<C> {

  /** An action that does nothing. */
  static <C> Action<C> none() {
    return context -> {};
  }

  /** Performs the action for {@code context}. */
  void apply(C context);
}
