package edu.ums.platform.concurrent;

/**
 * Receives failures that escaped a managed thread. The application wires an implementation that
 * writes to {@code app_error_log}; implementations must be fast and must not throw.
 */
@FunctionalInterface
public interface UncaughtErrorSink {

  /** A sink that records nothing (the failure is still logged by the thread factory). */
  UncaughtErrorSink NONE = (threadName, error) -> {};

  /** Records that {@code error} terminated a task on {@code threadName}. */
  void record(String threadName, Throwable error);
}
