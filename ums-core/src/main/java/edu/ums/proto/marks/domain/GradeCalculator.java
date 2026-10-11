package edu.ums.proto.marks.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Weighted totals and grades: total = Σ(value ÷ max × weight), absent counts 0, scale 2 HALF_UP;
 * the grade is the scale entry with the greatest minimum percent ≤ total ({@code floorEntry}).
 */
// Complexity: time O(c) per total for c components; O(log g) per grade for g scale rows (<= 10);
// space O(g)
public final class GradeCalculator {

  /** A grade and its point. */
  public record Grade(String letter, BigDecimal point) {}

  /** A component's maximum and weight. */
  public record Component(long id, BigDecimal maxMarks, BigDecimal weightPct) {}

  /** One student's mark for a component; {@code value} may be null when absent. */
  public record Mark(long componentId, BigDecimal value, boolean absent) {}

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final NavigableMap<BigDecimal, Grade> scale;

  /** Creates a calculator for a scale keyed by minimum percent. */
  public GradeCalculator(Map<BigDecimal, Grade> scale) {
    TreeMap<BigDecimal, Grade> copy = new TreeMap<>(scale);
    if (copy.isEmpty() || copy.firstKey().signum() > 0) {
      throw new IllegalArgumentException("scale must start at 0");
    }
    this.scale = java.util.Collections.unmodifiableNavigableMap(copy);
  }

  /** The default 10-point scale (same as V901). */
  public static GradeCalculator standard() {
    Map<BigDecimal, Grade> scale = new TreeMap<>();
    String[] letters = {"O", "A+", "A", "B+", "B", "C", "P", "F"};
    int[] mins = {90, 80, 70, 60, 50, 45, 40, 0};
    int[] points = {10, 9, 8, 7, 6, 5, 4, 0};
    for (int i = 0; i < letters.length; i++) {
      scale.put(BigDecimal.valueOf(mins[i]), new Grade(letters[i], BigDecimal.valueOf(points[i])));
    }
    return new GradeCalculator(scale);
  }

  /** Weighted total of one student's marks, scale 2 HALF_UP; missing or absent marks count 0. */
  public BigDecimal total(List<Component> components, Map<Long, Mark> marks) {
    BigDecimal sum = BigDecimal.ZERO;
    for (Component component : components) {
      Mark mark = marks.get(component.id());
      if (mark == null || mark.absent() || mark.value() == null) {
        continue;
      }
      sum =
          sum.add(
              mark.value()
                  .multiply(component.weightPct())
                  .divide(component.maxMarks(), 10, RoundingMode.HALF_UP));
    }
    return sum.min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
  }

  /** The grade for a total percent. */
  public Grade grade(BigDecimal totalPct) {
    Objects.requireNonNull(totalPct, "totalPct");
    return scale.floorEntry(totalPct.max(BigDecimal.ZERO)).getValue();
  }
}
