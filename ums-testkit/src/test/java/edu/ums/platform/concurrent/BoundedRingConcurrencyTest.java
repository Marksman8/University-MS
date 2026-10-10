package edu.ums.platform.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.testkit.ConcurrencyHarness;
import edu.ums.testkit.ConcurrencyResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;

/** BoundedRing never exceeds its capacity under 32 concurrent writers and a concurrent reader. */
class BoundedRingConcurrencyTest {

  private static final int CAPACITY = 500;
  private static final int WRITERS = 32;
  private static final int PER_WRITER = 1_000;

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

      ConcurrencyResult<Integer> result =
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
      assertThat(result.outcomes()).hasSize(WRITERS);
      assertThat(sequence).hasValue(WRITERS * PER_WRITER);
      List<Integer> snapshot = ring.snapshot();
      assertThat(snapshot).hasSize(CAPACITY).doesNotHaveDuplicates();
      assertThat(ring.size()).isEqualTo(CAPACITY);
      assertThat(maxSeen.get()).isLessThanOrEqualTo(CAPACITY);
    }
  }
}
