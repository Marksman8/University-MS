package edu.ums.platform.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.BusyException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ManagedExecutorsTest {

  @Test
  void shutdownRunsStagesInTheExactDocumentedSequence() {
    List<String> actuallyStopped = Collections.synchronizedList(new ArrayList<>());
    ManagedExecutors executors = new ManagedExecutors(UncaughtErrorSink.NONE);

    // Registered deliberately out of stage order.
    executors.fixedPool("workers", 2, 8, RejectionMode.REJECT_BUSY);
    executors.register(
        ShutdownStage.POOLS, "connection-pools", recorder("connection-pools", actuallyStopped));
    executors.register(
        ShutdownStage.LISTENERS,
        "activity-listener",
        recorder("activity-listener", actuallyStopped));
    executors.scheduler("scheduler", 1);
    executors.register(
        ShutdownStage.DRAINERS, "audit-writer", recorder("audit-writer", actuallyStopped));
    executors.register(
        ShutdownStage.ACCEPT_STOP, "readiness", recorder("readiness", actuallyStopped));
    executors.register(
        ShutdownStage.LISTENERS,
        "listener-dispatch",
        recorder("listener-dispatch", actuallyStopped));

    List<String> sequence = executors.shutdownAll(Duration.ofSeconds(2));

    assertThat(sequence)
        .containsExactly(
            "ACCEPT_STOP:readiness",
            "DRAINERS:audit-writer",
            "SCHEDULER:scheduler",
            "LISTENERS:activity-listener",
            "LISTENERS:listener-dispatch",
            "POOLS:workers",
            "POOLS:connection-pools");
    assertThat(actuallyStopped)
        .containsExactly(
            "readiness",
            "audit-writer",
            "activity-listener",
            "listener-dispatch",
            "connection-pools");
  }

  @Test
  void shutdownIsIdempotentAndClosesRegistration() {
    ManagedExecutors executors = new ManagedExecutors(UncaughtErrorSink.NONE);
    executors.register(ShutdownStage.POOLS, "x", timeout -> {});
    assertThat(executors.shutdownAll(Duration.ofSeconds(1))).containsExactly("POOLS:x");
    assertThat(executors.shutdownAll(Duration.ofSeconds(1))).isEmpty();
    assertThatIllegalStateException()
        .isThrownBy(() -> executors.register(ShutdownStage.POOLS, "late", timeout -> {}));
  }

  @Test
  void failingComponentDoesNotStopTheSequence() {
    List<String> stopped = new ArrayList<>();
    ManagedExecutors executors = new ManagedExecutors(UncaughtErrorSink.NONE);
    executors.register(
        ShutdownStage.DRAINERS,
        "broken",
        timeout -> {
          throw new IllegalStateException("boom");
        });
    executors.register(ShutdownStage.POOLS, "after", recorder("after", stopped));

    assertThat(executors.shutdownAll(Duration.ofSeconds(1)))
        .containsExactly("DRAINERS:broken", "POOLS:after");
    assertThat(stopped).containsExactly("after");
  }

  @Test
  void fixedPoolUsesNamedDaemonThreadsAndStopsOnShutdown() throws Exception {
    ManagedExecutors executors = new ManagedExecutors(UncaughtErrorSink.NONE);
    ExecutorService pool = executors.fixedPool("health", 1, 4, RejectionMode.REJECT_BUSY);
    AtomicReference<Thread> thread = new AtomicReference<>();

    pool.submit(() -> thread.set(Thread.currentThread())).get(5, TimeUnit.SECONDS);
    executors.shutdownAll(Duration.ofSeconds(2));

    assertThat(thread.get().getName()).isEqualTo("health-1");
    assertThat(thread.get().isDaemon()).isTrue();
    assertThat(pool.isTerminated()).isTrue();
  }

  @Test
  void fullQueueRejectsWithBusyOrRunsOnCaller() throws Exception {
    ManagedExecutors executors = new ManagedExecutors(UncaughtErrorSink.NONE);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService busy = executors.fixedPool("busy", 1, 1, RejectionMode.REJECT_BUSY);
    ExecutorService caller = executors.fixedPool("caller", 1, 1, RejectionMode.CALLER_RUNS);
    try {
      Runnable block = () -> awaitQuietly(release);
      busy.execute(block); // occupies the thread
      busy.execute(block); // fills the queue
      assertThatThrownBy(() -> busy.execute(block))
          .isInstanceOfSatisfying(
              BusyException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSY_TRY_AGAIN));

      caller.execute(block);
      caller.execute(block);
      AtomicReference<String> ranOn = new AtomicReference<>();
      caller.execute(() -> ranOn.set(Thread.currentThread().getName()));
      assertThat(ranOn.get()).isEqualTo(Thread.currentThread().getName());
    } finally {
      release.countDown();
      executors.shutdownAll(Duration.ofSeconds(2));
    }
  }

  @Test
  void uncaughtFailureIsForwardedToTheSink() throws InterruptedException {
    CountDownLatch recorded = new CountDownLatch(1);
    AtomicReference<String> where = new AtomicReference<>();
    ManagedExecutors executors =
        new ManagedExecutors(
            (threadName, error) -> {
              where.set(threadName + ":" + error.getMessage());
              recorded.countDown();
            });
    ExecutorService pool = executors.fixedPool("crashy", 1, 2, RejectionMode.REJECT_BUSY);

    pool.execute(
        () -> {
          throw new IllegalStateException("escaped");
        });

    assertThat(recorded.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(where.get()).isEqualTo("crashy-1:escaped");
    executors.shutdownAll(Duration.ofSeconds(2));
  }

  private static Stoppable recorder(String name, List<String> log) {
    return timeout -> log.add(name);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
