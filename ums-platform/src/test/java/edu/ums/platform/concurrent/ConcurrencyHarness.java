package edu.ums.platform.concurrent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Starts {@code threads} copies of a task at the same instant (one {@link CountDownLatch}) and
 * collects every outcome and failure. Local to ums-platform until the testkit exists (Step 12).
 */
final class ConcurrencyHarness {

  private ConcurrencyHarness() {}

  /** Number of repetitions requested via {@code -Dums.concurrency.repeats} (default 1). */
  static int repeats() {
    return Integer.getInteger("ums.concurrency.repeats", 1);
  }

  static <T> Result<T> run(int threads, Callable<T> task) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    long start = System.nanoTime();
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
      go.countDown();
      List<T> outcomes = new ArrayList<>(threads);
      List<Throwable> failures = new ArrayList<>();
      for (Future<T> future : futures) {
        try {
          outcomes.add(future.get(30, TimeUnit.SECONDS));
        } catch (ExecutionException e) {
          failures.add(e.getCause());
        } catch (TimeoutException e) {
          failures.add(e);
        }
      }
      return new Result<>(outcomes, failures, Duration.ofNanos(System.nanoTime() - start));
    } finally {
      pool.shutdownNow();
    }
  }

  record Result<T>(List<T> outcomes, List<Throwable> failures, Duration elapsed) {}
}
