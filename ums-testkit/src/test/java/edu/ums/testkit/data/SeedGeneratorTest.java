package edu.ums.testkit.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SeedGeneratorTest {

  @Test
  void sameSeedYieldsTheSameSequence() {
    SeedGenerator a = TestData.seeded(7);
    SeedGenerator b = TestData.seeded(7);
    List<Integer> fromA = IntStream.range(0, 50).mapToObj(i -> a.between(1, 1000)).toList();
    List<Integer> fromB = IntStream.range(0, 50).mapToObj(i -> b.between(1, 1000)).toList();
    assertThat(fromA).isEqualTo(fromB);
    assertThat(a.pick(List.of("x", "y", "z"))).isEqualTo(b.pick(List.of("x", "y", "z")));
  }

  @Test
  void valuesStayInRangeAndUsernamesAreUnique() {
    SeedGenerator generator = TestData.defaults();
    assertThat(generator.seed()).isEqualTo(TestData.DEFAULT_SEED);
    IntStream.range(0, 1_000).forEach(i -> assertThat(generator.between(3, 5)).isBetween(3, 5));
    assertThat(generator.username("student")).isEqualTo("student-000001");
    assertThat(generator.username("student")).isEqualTo("student-000002");
  }
}
