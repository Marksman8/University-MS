package edu.ums.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class PageRequestTest {

  @Property
  void sizesWithinBoundsAreAccepted(@ForAll @IntRange(min = 1, max = 200) int size) {
    assertThat(PageRequest.first(size).size()).isEqualTo(size);
  }

  @Property
  void sizesOutsideBoundsAreRejected(@ForAll("outOfRange") int size) {
    assertThatThrownBy(() -> new PageRequest(Optional.empty(), size))
        .isInstanceOfSatisfying(
            UmsApiException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Provide
  Arbitrary<Integer> outOfRange() {
    return Arbitraries.oneOf(
        Arbitraries.integers().lessOrEqual(0),
        Arbitraries.integers().greaterOrEqual(201),
        Arbitraries.of(0, 201, Integer.MIN_VALUE, Integer.MAX_VALUE));
  }
}
