package edu.ums.platform.concurrent;

import java.time.Duration;

/** A component that can be stopped within a time budget as part of the shutdown sequence. */
@FunctionalInterface
public interface Stoppable {

  /**
   * Stops the component, waiting at most {@code timeout}. Must return (not hang) once the budget is
   * used up; must restore the interrupt flag if interrupted.
   */
  void stop(Duration timeout);
}
