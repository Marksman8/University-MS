package edu.ums.platform.observe;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HealthServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
  private static final Duration TIMEOUT = Duration.ofMillis(300);

  private final ExecutorService executor =
      new ThreadPoolExecutor(
          4,
          4,
          0,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(8),
          new ThreadPoolExecutor.AbortPolicy());
  private final CountDownLatch never = new CountDownLatch(1);

  @AfterEach
  void stop() {
    never.countDown();
    executor.shutdownNow();
  }

  @Test
  void allProbesUpIsUpInRegistrationOrder() {
    HealthReport report =
        service(fixed("database", HealthStatus.UP), fixed("migrations", HealthStatus.UP)).check();
    assertThat(report.status()).isEqualTo(HealthStatus.UP);
    assertThat(report.probes())
        .extracting(ProbeResult::name)
        .containsExactly("database", "migrations");
    assertThat(report.checkedAt()).isEqualTo(NOW);
  }

  @Test
  void overallIsTheWorstProbe() {
    assertThat(
            service(fixed("a", HealthStatus.UP), fixed("b", HealthStatus.DEGRADED))
                .check()
                .status())
        .isEqualTo(HealthStatus.DEGRADED);
    assertThat(
            service(fixed("a", HealthStatus.DOWN), fixed("b", HealthStatus.DEGRADED))
                .check()
                .status())
        .isEqualTo(HealthStatus.DOWN);
    assertThat(service().check().status()).isEqualTo(HealthStatus.UP);
  }

  @Test
  void slowProbeIsDownAfterTheTimeoutWithoutBlockingTheCheck() {
    HealthProbe slow =
        probe(
            "slow",
            () -> {
              awaitQuietly(never);
              return new ProbeResult("slow", HealthStatus.UP, "late");
            });
    long start = System.nanoTime();

    HealthReport report = service(slow, fixed("fast", HealthStatus.UP)).check();

    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(millis).isLessThan(2_000);
    assertThat(report.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(report.probes().get(0).detail()).isEqualTo("timed out after 300 ms");
    assertThat(report.probes().get(1).status()).isEqualTo(HealthStatus.UP);
  }

  @Test
  void throwingProbeIsDown() {
    HealthProbe broken =
        probe(
            "broken",
            () -> {
              throw new IllegalStateException("boom");
            });
    ProbeResult result = service(broken).check().probes().get(0);
    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.detail()).isEqualTo("failed: IllegalStateException");
  }

  @Test
  void probeThatCannotBeScheduledIsDown() {
    executor.shutdown();
    ProbeResult result = service(fixed("db", HealthStatus.UP)).check().probes().get(0);
    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.detail()).startsWith("not scheduled");
  }

  @Test
  void worstCombinesStatuses() {
    assertThat(HealthStatus.UP.worst(HealthStatus.DOWN)).isEqualTo(HealthStatus.DOWN);
    assertThat(HealthStatus.DEGRADED.worst(HealthStatus.UP)).isEqualTo(HealthStatus.DEGRADED);
  }

  private HealthService service(HealthProbe... probes) {
    return new HealthService(List.of(probes), executor, TIMEOUT, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static HealthProbe fixed(String name, HealthStatus status) {
    return probe(name, () -> new ProbeResult(name, status, status.name().toLowerCase()));
  }

  private static HealthProbe probe(String name, java.util.function.Supplier<ProbeResult> check) {
    return new HealthProbe() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public ProbeResult check() {
        return check.get();
      }
    };
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
