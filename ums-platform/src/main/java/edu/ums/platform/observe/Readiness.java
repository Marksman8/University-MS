package edu.ums.platform.observe;

import edu.ums.platform.error.DataUnavailableException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether the application may serve facade calls. Off until startup completes and again from the
 * first shutdown stage. The SESSION interceptor (P1) calls {@link #requireReady()} first.
 */
// Complexity: time O(1) per call; space O(1)
public final class Readiness {

  private final AtomicBoolean ready = new AtomicBoolean(false);

  /** Marks the application ready to serve. */
  public void markReady() {
    ready.set(true);
  }

  /** Stops serving new calls. */
  public void markNotReady() {
    ready.set(false);
  }

  /** Returns {@code true} while the application serves calls. */
  public boolean isReady() {
    return ready.get();
  }

  /**
   * Fails unless ready.
   *
   * @throws DataUnavailableException ({@code DB_UNAVAILABLE}) while not ready
   */
  public void requireReady() {
    if (!ready.get()) {
      throw new DataUnavailableException(new IllegalStateException("application not ready"));
    }
  }
}
