package edu.ums.testkit.data;

/**
 * Root entry point for test data. All test data comes from here (CLAUDE.md §9); feature phases add
 * builders (students, sections, terms, ...) as methods taking a {@link SeedGenerator}.
 */
public final class TestData {

  /** Seed used when a test does not care about the exact values. */
  public static final long DEFAULT_SEED = 20_261_010L;

  private TestData() {}

  /** Returns a generator for {@code seed}. */
  public static SeedGenerator seeded(long seed) {
    return new SeedGenerator(seed);
  }

  /** Returns a generator for {@link #DEFAULT_SEED}. */
  public static SeedGenerator defaults() {
    return seeded(DEFAULT_SEED);
  }
}
