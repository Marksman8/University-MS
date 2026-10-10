package edu.ums.testkit;

import java.time.Duration;
import java.util.List;

/**
 * What a {@link ConcurrencyHarness} run produced.
 *
 * @param outcomes results of the tasks that completed normally
 * @param failures the exception of each task that failed or timed out
 * @param elapsed time from release to the last collected result
 */
public record ConcurrencyResult<T>(List<T> outcomes, List<Throwable> failures, Duration elapsed) {

  /** Copies the lists. */
  public ConcurrencyResult {
    outcomes = List.copyOf(outcomes);
    failures = List.copyOf(failures);
  }
}
