package edu.ums.platform.observe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-method call counters: count, total nanoseconds and errors, as lock-free {@link LongAdder}s.
 * Keys are facade method names (a finite set); at most {@value #MAX_KEYS} distinct keys are kept,
 * further keys are counted under {@value #OVERFLOW_KEY}.
 */
// Complexity: time O(1) per record (hash lookup + 3 adds); space O(m) — m <= MAX_KEYS methods
public final class MetricsRegistry {

  /** Upper bound on distinct keys. */
  public static final int MAX_KEYS = 1_024;

  /** Key that absorbs calls once {@link #MAX_KEYS} is reached. */
  public static final String OVERFLOW_KEY = "_overflow";

  private final ConcurrentHashMap<String, Counters> counters = new ConcurrentHashMap<>();

  /** Records one call of {@code key} that took {@code nanos} and did or did not fail. */
  public void record(String key, long nanos, boolean failed) {
    Counters c = counters.get(key);
    if (c == null) {
      String effective = counters.size() >= MAX_KEYS ? OVERFLOW_KEY : key;
      c = counters.computeIfAbsent(effective, k -> new Counters());
    }
    c.count.increment();
    c.totalNanos.add(nanos);
    if (failed) {
      c.errors.increment();
    }
  }

  /** Returns an immutable point-in-time copy of every key's counters. */
  public Map<String, MethodStats> snapshot() {
    Map<String, MethodStats> copy = new java.util.HashMap<>();
    counters.forEach(
        (key, c) ->
            copy.put(key, new MethodStats(c.count.sum(), c.totalNanos.sum(), c.errors.sum())));
    return Map.copyOf(copy);
  }

  /** Immutable counter values for one method. */
  public record MethodStats(long count, long totalNanos, long errors) {}

  private static final class Counters {
    private final LongAdder count = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final LongAdder errors = new LongAdder();
  }
}
