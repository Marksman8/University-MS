package edu.ums.testkit.data;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic source of synthetic test values: the same seed always yields the same sequence.
 * Feature phases add builders on top of it. Never produces real personal data. Not thread-safe; use
 * one generator per test.
 */
public final class SeedGenerator {

  private final long seed;
  private final SplittableRandom random;
  private long sequence;

  /** Creates a generator for {@code seed}. */
  public SeedGenerator(long seed) {
    this.seed = seed;
    this.random = new SplittableRandom(seed);
  }

  /** Returns the seed, for reproducing a failing test. */
  public long seed() {
    return seed;
  }

  /** Returns the next value of a per-generator sequence, starting at 1. */
  public long nextSequence() {
    return ++sequence;
  }

  /** Returns a uniformly random int in {@code [min, max]}. */
  public int between(int min, int max) {
    return random.nextInt(min, max + 1);
  }

  /** Returns a uniformly random element of {@code options}. */
  public <T> T pick(List<T> options) {
    return options.get(random.nextInt(options.size()));
  }

  /** Returns a unique synthetic username such as {@code student-000042}. */
  public String username(String prefix) {
    return prefix + "-" + String.format("%06d", nextSequence());
  }
}
