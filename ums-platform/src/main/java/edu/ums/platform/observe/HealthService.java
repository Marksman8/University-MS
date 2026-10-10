package edu.ums.platform.observe;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs every {@link HealthProbe} in parallel on a managed executor, each bounded by the same
 * timeout (2 s in production). A probe that throws, times out or cannot be scheduled is reported as
 * {@link HealthStatus#DOWN}; the overall status is the worst probe status.
 */
// Complexity: time O(p) per check plus at most one timeout of wall time; space O(p), p probes
public final class HealthService {

  private final List<HealthProbe> probes;
  private final ExecutorService executor;
  private final Duration timeout;
  private final Clock clock;

  /** Creates the service; {@code executor} must be a bounded, managed pool. */
  public HealthService(
      List<HealthProbe> probes, ExecutorService executor, Duration timeout, Clock clock) {
    this.probes = List.copyOf(probes);
    this.executor = Objects.requireNonNull(executor, "executor");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Runs all probes and returns the combined report. Never throws. */
  public HealthReport check() {
    java.time.Instant checkedAt = clock.instant();
    List<Future<ProbeResult>> futures = new ArrayList<>(probes.size());
    List<ProbeResult> results = new ArrayList<>(probes.size());
    for (HealthProbe probe : probes) {
      futures.add(submit(probe));
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    HealthStatus overall = HealthStatus.UP;
    for (int i = 0; i < probes.size(); i++) {
      ProbeResult result = await(probes.get(i), futures.get(i), deadline);
      results.add(result);
      overall = overall.worst(result.status());
    }
    return new HealthReport(overall, results, checkedAt);
  }

  private Future<ProbeResult> submit(HealthProbe probe) {
    try {
      return executor.submit(probe::check);
    } catch (RuntimeException busy) { // CHECKSTYLE:ALLOW-CATCH best-effort probe scheduling
      return CompletableFuture.completedFuture(
          down(probe, "not scheduled: " + busy.getClass().getSimpleName()));
    }
  }

  private ProbeResult await(HealthProbe probe, Future<ProbeResult> future, long deadline) {
    try {
      long remaining = Math.max(0, deadline - System.nanoTime());
      ProbeResult result = future.get(remaining, TimeUnit.NANOSECONDS);
      return result == null ? down(probe, "no result") : result;
    } catch (TimeoutException slow) {
      future.cancel(true);
      return down(probe, "timed out after " + timeout.toMillis() + " ms");
    } catch (ExecutionException failed) {
      return down(probe, "failed: " + failed.getCause().getClass().getSimpleName());
    } catch (InterruptedException interrupted) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      return down(probe, "interrupted");
    }
  }

  private static ProbeResult down(HealthProbe probe, String detail) {
    return new ProbeResult(probe.name(), HealthStatus.DOWN, detail);
  }
}
