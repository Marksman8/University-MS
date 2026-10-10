package edu.ums.platform.concurrent;

/** What a fixed pool does when its bounded queue is full. */
public enum RejectionMode {
  /** The submitting thread runs the task itself (back-pressure). */
  CALLER_RUNS,
  /**
   * The submission fails with {@link edu.ums.platform.error.BusyException} ({@code
   * BUSY_TRY_AGAIN}).
   */
  REJECT_BUSY
}
