package edu.ums.platform.concurrent;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.InfrastructureException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodic jobs on a {@link ScheduledThreadPoolExecutor}. Each job is wrapped so that an exception
 * is logged and the schedule continues (a plain executor would silently cancel it). Only fixed-rate
 * jobs can be added, at most {@value #MAX_JOBS}, so the executor's internal delay queue holds at
 * most that many entries.
 */
// Complexity: time O(log j) per schedule; space O(j) — j jobs, j <= MAX_JOBS
public final class Scheduler implements Stoppable {

  /** Maximum number of scheduled jobs. */
  public static final int MAX_JOBS = 32;

  private static final Logger LOG = LoggerFactory.getLogger(Scheduler.class);

  private final ScheduledThreadPoolExecutor executor;
  private final AtomicInteger jobs = new AtomicInteger();

  /** Creates a scheduler with {@code threads} threads from {@code factory}. */
  public Scheduler(int threads, NamedThreadFactory factory) {
    this.executor = new ScheduledThreadPoolExecutor(threads, factory);
    this.executor.setRemoveOnCancelPolicy(true);
    this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
  }

  /**
   * Runs {@code job} every {@code period} after {@code initialDelay}.
   *
   * @throws InfrastructureException {@code INTERNAL_ERROR} beyond {@link #MAX_JOBS} jobs
   */
  public ScheduledFuture<?> scheduleAtFixedRate(
      String name, Duration initialDelay, Duration period, Runnable job) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(job, "job");
    if (jobs.incrementAndGet() > MAX_JOBS) {
      jobs.decrementAndGet();
      throw new InfrastructureException(
          ErrorCode.INTERNAL_ERROR, new IllegalStateException("more than " + MAX_JOBS + " jobs"));
    }
    return executor.scheduleAtFixedRate(
        () -> runGuarded(name, job),
        initialDelay.toMillis(),
        period.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /** Stops all jobs, waiting up to {@code timeout} for running ones to finish. */
  @Override
  public void stop(Duration timeout) {
    ExecutorLifecycle.shutdownAndAwait(executor, timeout);
  }

  private static void runGuarded(String name, Runnable job) {
    try {
      job.run();
    } catch (RuntimeException failure) { // CHECKSTYLE:ALLOW-CATCH keep the schedule alive
      LOG.error("Scheduled job {} failed; it will run again at its next period", name, failure);
    }
  }
}
