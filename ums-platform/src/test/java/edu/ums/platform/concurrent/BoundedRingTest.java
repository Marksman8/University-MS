package edu.ums.platform.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class BoundedRingTest {

  private static final int CAPACITY = 500;
  private static final int WRITERS = 32;
  private static final int PER_WRITER = 1_000;

  @Test
  void keepsTheNewestItemsNewestFirst() {
    BoundedRing<Integer> ring = new BoundedRing<>(3);
    assertThat(ring.snapshot()).isEmpty();
    for (int i = 1; i <= 5; i++) {
      ring.add(i);
    }
    assertThat(ring.snapshot()).containsExactly(5, 4, 3);
    assertThat(ring.size()).isEqualTo(3);
    assertThat(ring.capacity()).isEqualTo(3);
  }

  @Test
  void partiallyFilledRingReturnsOnlyWrittenItems() {
    BoundedRing<String> ring = new BoundedRing<>(4);
    ring.add("a");
    ring.add("b");
    assertThat(ring.snapshot()).containsExactly("b", "a");
    assertThat(ring.size()).isEqualTo(2);
  }

  @Test
  void rejectsNonPositiveCapacity() {
    assertThatIllegalArgumentException().isThrownBy(() -> new BoundedRing<>(0));
  }

  @RepeatedTest(3)
  void neverExceedsCapacityUnder32ConcurrentWriters() throws InterruptedException {
    for (int repeat = 0; repeat < ConcurrencyHarness.repeats(); repeat++) {
      BoundedRing<Integer> ring = new BoundedRing<>(CAPACITY);
      AtomicInteger sequence = new AtomicInteger();
      AtomicBoolean writing = new AtomicBoolean(true);
      AtomicInteger maxSeen = new AtomicInteger();
      Thread reader =
          new Thread(
              () -> {
                while (writing.get()) {
                  maxSeen.accumulateAndGet(ring.snapshot().size(), Math::max);
                }
              },
              "ring-reader");
      reader.start();

      ConcurrencyHarness.Result<Integer> result =
          ConcurrencyHarness.run(
              WRITERS,
              () -> {
                for (int i = 0; i < PER_WRITER; i++) {
                  ring.add(sequence.incrementAndGet());
                }
                return PER_WRITER;
              });
      writing.set(false);
      reader.join();

      assertThat(result.failures()).isEmpty();
      assertThat(sequence).hasValue(WRITERS * PER_WRITER);
      List<Integer> snapshot = ring.snapshot();
      assertThat(snapshot).hasSize(CAPACITY).doesNotHaveDuplicates();
      assertThat(ring.size()).isEqualTo(CAPACITY);
      assertThat(maxSeen.get()).isLessThanOrEqualTo(CAPACITY);
    }
  }
}
