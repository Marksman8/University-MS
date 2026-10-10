package edu.ums.platform.config;

import java.util.Arrays;
import java.util.Objects;

/**
 * A secret value (password) held as a {@code char[]} so it can be zeroed. Never printed: {@link
 * #toString()} returns {@code ****}. Thread-safe.
 */
public final class Secret {

  private static final String MASK = "****";

  private final Object lock = new Object();
  private final char[] value;
  private boolean cleared;

  private Secret(char[] value) {
    this.value = value;
  }

  /** Wraps a copy of {@code value}. */
  public static Secret of(String value) {
    return new Secret(Objects.requireNonNull(value, "value").toCharArray());
  }

  /**
   * Returns a copy of the secret. The caller should zero the copy when done.
   *
   * @throws IllegalStateException if {@link #clear()} was called
   */
  public char[] reveal() {
    synchronized (lock) {
      if (cleared) {
        throw new IllegalStateException("Secret has been cleared");
      }
      return value.clone();
    }
  }

  /** Returns {@code true} once {@link #clear()} has been called. */
  public boolean isCleared() {
    synchronized (lock) {
      return cleared;
    }
  }

  /** Overwrites the secret with zeros. Idempotent. */
  public void clear() {
    synchronized (lock) {
      Arrays.fill(value, '\0');
      cleared = true;
    }
  }

  @Override
  public String toString() {
    return MASK;
  }
}
