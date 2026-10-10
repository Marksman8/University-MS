package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.platform.error.ConflictException;
import edu.ums.platform.error.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  private final List<Long> sleeps = new ArrayList<>();
  private final RetryPolicy policy = new RetryPolicy(new Random(3), sleeps::add);

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void succeedsAfterTwoConflicts() {
    AtomicInteger calls = new AtomicInteger();
    String result =
        policy.execute(
            () -> {
              if (calls.incrementAndGet() < 3) {
                throw new ConflictException(null);
              }
              return "ok";
            });
    assertThat(result).isEqualTo("ok");
    assertThat(calls).hasValue(3);
    assertThat(sleeps).hasSize(2);
  }

  @Test
  void givesUpAfterThreeAttemptsInTotal() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                policy.execute(
                    () -> {
                      calls.incrementAndGet();
                      throw new ConflictException(null);
                    }))
        .isInstanceOf(ConflictException.class);
    assertThat(calls).hasValue(RetryPolicy.MAX_ATTEMPTS);
    assertThat(sleeps).hasSize(RetryPolicy.MAX_ATTEMPTS - 1);
  }

  @Test
  void otherFailuresAreNotRetried() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                policy.execute(
                    () -> {
                      calls.incrementAndGet();
                      throw new NotFoundException(Map.of());
                    }))
        .isInstanceOf(NotFoundException.class);
    assertThat(calls).hasValue(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void backoffStaysWithinBounds() {
    for (int i = 0; i < 10_000; i++) {
      assertThat(policy.nextBackoffMillis())
          .isBetween((long) RetryPolicy.MIN_BACKOFF_MS, (long) RetryPolicy.MAX_BACKOFF_MS);
    }
  }

  @Test
  void interruptDuringBackoffStopsRetryingAndRestoresFlag() {
    RetryPolicy interrupting =
        new RetryPolicy(
            new Random(1),
            millis -> {
              throw new InterruptedException("stop");
            });
    AtomicInteger calls = new AtomicInteger();

    assertThatThrownBy(
            () ->
                interrupting.execute(
                    () -> {
                      calls.incrementAndGet();
                      throw new ConflictException(null);
                    }))
        .isInstanceOf(ConflictException.class)
        .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    assertThat(calls).hasValue(1);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }
}
