package edu.ums.platform.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.platform.error.InfrastructureException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SchedulerTest {

  private final Scheduler scheduler =
      new Scheduler(2, new NamedThreadFactory("test-scheduler", true, UncaughtErrorSink.NONE));

  @AfterEach
  void stop() {
    scheduler.stop(Duration.ofSeconds(2));
  }

  @Test
  void failingJobStillRunsNextTime() throws InterruptedException {
    AtomicInteger runs = new AtomicInteger();
    CountDownLatch laterRuns = new CountDownLatch(2);
    scheduler.scheduleAtFixedRate(
        "flaky",
        Duration.ZERO,
        Duration.ofMillis(10),
        () -> {
          if (runs.incrementAndGet() == 1) {
            throw new IllegalStateException("first run fails");
          }
          laterRuns.countDown();
        });

    assertThat(laterRuns.await(5, TimeUnit.SECONDS)).as("job ran again after failing").isTrue();
    assertThat(runs.get()).isGreaterThanOrEqualTo(3);
  }

  @Test
  void jobsRunOnNamedSchedulerThreads() throws InterruptedException {
    List<String> names = new ArrayList<>();
    CountDownLatch ran = new CountDownLatch(1);
    scheduler.scheduleAtFixedRate(
        "named",
        Duration.ZERO,
        Duration.ofSeconds(10),
        () -> {
          synchronized (names) {
            names.add(Thread.currentThread().getName());
          }
          ran.countDown();
        });
    assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
    synchronized (names) {
      assertThat(names.get(0)).startsWith("test-scheduler-");
    }
  }

  @Test
  void refusesMoreThanMaxJobs() {
    for (int i = 0; i < Scheduler.MAX_JOBS; i++) {
      scheduler.scheduleAtFixedRate("job" + i, Duration.ofHours(1), Duration.ofHours(1), () -> {});
    }
    assertThatThrownBy(
            () ->
                scheduler.scheduleAtFixedRate(
                    "one-too-many", Duration.ofHours(1), Duration.ofHours(1), () -> {}))
        .isInstanceOf(InfrastructureException.class);
  }

  @Test
  void stopCancelsPeriodicJobs() throws InterruptedException {
    CountDownLatch first = new CountDownLatch(1);
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            "tick", Duration.ZERO, Duration.ofMillis(5), first::countDown);
    assertThat(first.await(5, TimeUnit.SECONDS)).isTrue();

    scheduler.stop(Duration.ofSeconds(2));

    assertThat(future.isDone()).isTrue();
  }
}
