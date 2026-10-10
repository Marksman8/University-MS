package edu.ums.platform.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Single-threaded behaviour; the 32-writer test lives in ums-testkit (BoundedRingConcurrencyTest).
 */
class BoundedRingTest {

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
}
