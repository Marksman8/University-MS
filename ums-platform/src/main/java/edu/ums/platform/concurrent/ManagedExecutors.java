package edu.ums.platform.concurrent;

import edu.ums.platform.error.BusyException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates every executor in the application and owns the shutdown sequence. Fixed pools have named
 * threads, a bounded {@link ArrayBlockingQueue} and an explicit rejection mode; everything created
 * or registered here is stopped by {@link #shutdownAll(Duration)} in {@link ShutdownStage} order,
 * then registration order within a stage.
 */
// Complexity: time O(r) per shutdownAll, r registrations (<= MAX_REGISTRATIONS); space O(r)
public final class ManagedExecutors {

  /** Upper bound on registered components. */
  public static final int MAX_REGISTRATIONS = 64;

  private static final Logger LOG = LoggerFactory.getLogger(ManagedExecutors.class);

  private final UncaughtErrorSink sink;
  private final Map<ShutdownStage, List<Registration>> stages = new EnumMap<>(ShutdownStage.class);
  private int registrations;
  private boolean shutDown;

  /** Creates the registry; {@code sink} receives failures that escape managed threads. */
  public ManagedExecutors(UncaughtErrorSink sink) {
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  /**
   * Creates a fixed pool of {@code threads} daemon threads named {@code name-n} with a queue of
   * {@code queueCapacity}; registered in {@link ShutdownStage#POOLS}.
   */
  public synchronized ExecutorService fixedPool(
      String name, int threads, int queueCapacity, RejectionMode mode) {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            new NamedThreadFactory(name, true, sink),
            handler(mode));
    register(
        ShutdownStage.POOLS, name, timeout -> ExecutorLifecycle.shutdownAndAwait(pool, timeout));
    return pool;
  }

  /**
   * Creates a scheduler with named daemon threads; registered in {@link ShutdownStage#SCHEDULER}.
   */
  public synchronized Scheduler scheduler(String name, int threads) {
    Scheduler scheduler = new Scheduler(threads, new NamedThreadFactory(name, true, sink));
    register(ShutdownStage.SCHEDULER, name, scheduler);
    return scheduler;
  }

  /**
   * Registers a component to stop in {@code stage}.
   *
   * @throws IllegalStateException after shutdown or beyond {@link #MAX_REGISTRATIONS}
   */
  public synchronized void register(ShutdownStage stage, String name, Stoppable component) {
    if (shutDown) {
      throw new IllegalStateException("already shut down");
    }
    if (registrations >= MAX_REGISTRATIONS) {
      throw new IllegalStateException("more than " + MAX_REGISTRATIONS + " registrations");
    }
    stages.computeIfAbsent(stage, s -> new ArrayList<>()).add(new Registration(name, component));
    registrations++;
  }

  /**
   * Stops everything: stage by stage in {@link ShutdownStage} order, registration order within a
   * stage, each stage within {@code perStage}. A failing component is logged and skipped.
   * Idempotent: later calls do nothing.
   *
   * @return the components stopped by this call, as {@code STAGE:name}, in order
   */
  public synchronized List<String> shutdownAll(Duration perStage) {
    if (shutDown) {
      return List.of();
    }
    shutDown = true;
    List<String> stopped = new ArrayList<>(registrations);
    for (ShutdownStage stage : ShutdownStage.values()) {
      long deadline = System.nanoTime() + perStage.toNanos();
      for (Registration registration : stages.getOrDefault(stage, List.of())) {
        Duration remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
        stopOne(stage, registration, remaining);
        stopped.add(stage + ":" + registration.name());
      }
    }
    return List.copyOf(stopped);
  }

  private static void stopOne(ShutdownStage stage, Registration registration, Duration budget) {
    try {
      registration.component().stop(budget);
    } catch (RuntimeException failure) { // CHECKSTYLE:ALLOW-CATCH best-effort shutdown continues
      LOG.error("Shutdown of {}:{} failed", stage, registration.name(), failure);
    }
  }

  private static RejectedExecutionHandler handler(RejectionMode mode) {
    return switch (mode) {
      case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
      case REJECT_BUSY ->
          (task, executor) -> {
            throw new BusyException(new RejectedExecutionException("queue full"));
          };
    };
  }

  private record Registration(String name, Stoppable component) {}
}
