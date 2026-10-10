package edu.ums.platform.concurrent;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Pure helpers for executor lifecycle. */
final class ExecutorLifecycle {

  private ExecutorLifecycle() {}

  /**
   * Shuts {@code executor} down, waits up to {@code timeout}, then interrupts what is still
   * running. Restores the interrupt flag if the waiting thread is interrupted.
   */
  static void shutdownAndAwait(ExecutorService executor, Duration timeout) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(Math.max(0, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException interrupted) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
