package edu.ums.platform.db;

import edu.ums.platform.error.ConflictException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Retries work that failed with a {@link ConflictException} (SQLState 40001 / 40P01): at most
 * {@value #MAX_ATTEMPTS} attempts in total, sleeping a uniform random {@value #MIN_BACKOFF_MS}–
 * {@value #MAX_BACKOFF_MS} ms between attempts. Any other failure propagates immediately. Applied
 * only around the outermost transaction (see {@link TransactionRunner}).
 */
// Complexity: time O(1) bookkeeping per attempt, at most 3 attempts; space O(1)
public final class RetryPolicy {

  /** Total attempts, including the first. */
  public static final int MAX_ATTEMPTS = 3;

  /** Smallest back-off between attempts. */
  public static final int MIN_BACKOFF_MS = 20;

  /** Largest back-off between attempts. */
  public static final int MAX_BACKOFF_MS = 100;

  private final RandomGenerator random;
  private final Sleeper sleeper;

  /** Creates a policy drawing back-off delays from {@code random}. */
  public RetryPolicy(RandomGenerator random, Sleeper sleeper) {
    this.random = Objects.requireNonNull(random, "random");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
  }

  /**
   * Runs {@code attempt}, retrying on conflict.
   *
   * @throws ConflictException the last conflict once attempts are exhausted, or immediately if the
   *     thread is interrupted during back-off (the interrupt flag is restored)
   */
  public <T> T execute(Supplier<T> attempt) {
    for (int n = 1; ; n++) {
      try {
        return attempt.get();
      } catch (ConflictException conflict) {
        if (n >= MAX_ATTEMPTS) {
          throw conflict;
        }
        backOff(conflict);
      }
    }
  }

  /** Returns a back-off delay in {@code [MIN_BACKOFF_MS, MAX_BACKOFF_MS]}. */
  long nextBackoffMillis() {
    return random.nextLong(MIN_BACKOFF_MS, MAX_BACKOFF_MS + 1L);
  }

  private void backOff(ConflictException conflict) {
    try {
      sleeper.sleep(nextBackoffMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      conflict.addSuppressed(interrupted);
      throw conflict;
    }
  }
}
