package edu.ums.app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import edu.ums.testkit.ArchitectureRules;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Applies every shared rule to the production classes on this module's classpath. */
class ArchitectureTest {

  private static final JavaClasses PRODUCTION = ArchitectureRules.productionClasses();

  static Stream<ArchRule> rules() {
    return ArchitectureRules.ALL.stream();
  }

  @ParameterizedTest
  @MethodSource("rules")
  void productionCodeFollowsTheRule(ArchRule rule) {
    rule.check(PRODUCTION);
  }
}
