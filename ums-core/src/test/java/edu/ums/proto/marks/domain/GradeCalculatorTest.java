package edu.ums.proto.marks.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradeCalculatorTest {

  private final GradeCalculator calc = GradeCalculator.standard();

  private static final List<GradeCalculator.Component> COMPONENTS =
      List.of(
          new GradeCalculator.Component(1, BigDecimal.valueOf(40), BigDecimal.valueOf(40)),
          new GradeCalculator.Component(2, BigDecimal.valueOf(60), BigDecimal.valueOf(60)));

  @ParameterizedTest(name = "{0}% -> {1} ({2})")
  @CsvSource({
    "39.99, F, 0",
    "40, P, 4",
    "44.99, P, 4",
    "45, C, 5",
    "84.5, A+, 9",
    "89.99, A+, 9",
    "90, O, 10",
    "100, O, 10",
    "0, F, 0"
  })
  void boundaries(String total, String grade, int point) {
    GradeCalculator.Grade g = calc.grade(new BigDecimal(total));
    assertThat(g.letter()).isEqualTo(grade);
    assertThat(g.point()).isEqualByComparingTo(BigDecimal.valueOf(point));
  }

  @Test
  void weightedTotalWithAbsentCountedAsZero() {
    Map<Long, GradeCalculator.Mark> marks =
        Map.of(
            1L, new GradeCalculator.Mark(1, new BigDecimal("30"), false),
            2L, new GradeCalculator.Mark(2, null, true));
    assertThat(calc.total(COMPONENTS, marks)).isEqualByComparingTo("30.00");
  }

  @Test
  void totalIsScaledTwoHalfUp() {
    // 33/40*40 + 41/60*60 = 33 + 41 = 74; 13.333/40*40 is exact; use thirds to exercise rounding
    List<GradeCalculator.Component> thirds =
        List.of(new GradeCalculator.Component(1, BigDecimal.valueOf(3), BigDecimal.valueOf(100)));
    Map<Long, GradeCalculator.Mark> marks =
        Map.of(1L, new GradeCalculator.Mark(1, BigDecimal.valueOf(2), false));
    assertThat(calc.total(thirds, marks)).isEqualByComparingTo("66.67");
    assertThat(calc.total(thirds, marks).scale()).isEqualTo(2);
  }

  @Test
  void missingMarksCountZeroAndAllAbsentIsF() {
    assertThat(calc.total(COMPONENTS, Map.of())).isEqualByComparingTo("0.00");
    assertThat(calc.grade(calc.total(COMPONENTS, Map.of())).letter()).isEqualTo("F");
  }
}
