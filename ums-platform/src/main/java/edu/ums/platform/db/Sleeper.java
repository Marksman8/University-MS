package edu.ums.platform.db;

/** Pauses the current thread; injected so retry back-off is testable without real waiting. */
@FunctionalInterface
public interface Sleeper {

  /** The real implementation, backed by {@link Thread#sleep(long)}. */
  Sleeper SYSTEM = Thread::sleep;

  /**
   * Sleeps for {@code millis} milliseconds.
   *
   * @throws InterruptedException if the thread is interrupted while sleeping
   */
  void sleep(long millis) throws InterruptedException;
}
