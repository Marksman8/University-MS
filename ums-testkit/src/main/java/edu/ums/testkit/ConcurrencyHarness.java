package edu.ums.testkit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Starts {@code threads} copies of a task at the same instant (all released by one {@link
 * CountDownLatch}) and collects every outcome and failure. {@link #repeats()} honours {@code
 * -Dums.concurrency.repeats} (50 in the nightly profile).
 */
public final class ConcurrencyHarness {

  /** Per-task wait before a task is reported as timed out. */
  public static final Duration TASK_TIMEOUT = Duration.ofSeconds(30);

  private ConcurrencyHarness() {}

  /** Returns how often concurrency tests should repeat their scenario (default 1). */
  public static int repeats() {
    return Integer.getInteger("ums.concurrency.repeats", 1);
  }

  /**
   * Runs {@code task} on {@code threads} threads released together.
   *
   * @return outcomes of successful tasks, failures (cause of each failed task) and elapsed time
   */
  public static <T> ConcurrencyResult<T> run(int threads, Callable<T> task)
      throws InterruptedException {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(threads));
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<T>> futures = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      ready.await();
      long start = System.nanoTime();
      go.countDown();
      return collect(futures, start);
    } finally {
      pool.shutdownNow();
    }
  }

  private static <T> ConcurrencyResult<T> collect(List<Future<T>> futures, long start)
      throws InterruptedException {
    List<T> outcomes = new ArrayList<>(futures.size());
    List<Throwable> failures = new ArrayList<>();
    for (Future<T> future : futures) {
      try {
        outcomes.add(future.get(TASK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
      } catch (ExecutionException e) {
        failures.add(e.getCause());
      } catch (TimeoutException e) {
        failures.add(e);
      }
    }
    return new ConcurrencyResult<>(outcomes, failures, Duration.ofNanos(System.nanoTime() - start));
  }
}
