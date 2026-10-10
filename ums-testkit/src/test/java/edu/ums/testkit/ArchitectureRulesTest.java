package edu.ums.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import edu.ums.api.archfixture.ApiUsingPlatform;
import edu.ums.core.archfixturea.domain.CycleA;
import edu.ums.core.archfixturea.domain.DomainUsingRepository;
import edu.ums.core.archfixturea.repository.RepositoryUsingService;
import edu.ums.core.archfixturea.service.CompliantService;
import edu.ums.core.archfixturea.service.MisplacedRepository;
import edu.ums.core.archfixturea.service.RogueInterceptor;
import edu.ums.core.archfixturea.service.ServiceUsingFacade;
import edu.ums.core.archfixturea.service.ServiceUsingJdbc;
import edu.ums.core.archfixtureb.domain.CycleB;
import edu.ums.testkit.archfixture.MutableStaticHolder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every architecture rule rejects a violating fixture (fixtures live under src/test only). */
class ArchitectureRulesTest {

  static Stream<Arguments> violations() {
    return Stream.of(
        violation(
            "API_DEPENDS_ON_NOTHING",
            ArchitectureRules.API_DEPENDS_ON_NOTHING,
            ApiUsingPlatform.class),
        violation(
            "LAYER_DIRECTION (repository -> service)",
            ArchitectureRules.LAYER_DIRECTION,
            RepositoryUsingService.class),
        violation(
            "LAYER_DIRECTION (domain -> repository)",
            ArchitectureRules.LAYER_DIRECTION,
            DomainUsingRepository.class),
        violation(
            "JDBC_ONLY_IN_DATA_ACCESS",
            ArchitectureRules.JDBC_ONLY_IN_DATA_ACCESS,
            ServiceUsingJdbc.class),
        violation(
            "NO_MUTABLE_STATIC_FIELDS",
            ArchitectureRules.NO_MUTABLE_STATIC_FIELDS,
            MutableStaticHolder.class),
        violation(
            "NO_FEATURE_CYCLES", ArchitectureRules.NO_FEATURE_CYCLES, CycleA.class, CycleB.class),
        violation(
            "SERVICES_DO_NOT_DEPEND_ON_FACADES",
            ArchitectureRules.SERVICES_DO_NOT_DEPEND_ON_FACADES,
            ServiceUsingFacade.class),
        violation(
            "INTERCEPTORS_ONLY_IN_INVOKE",
            ArchitectureRules.INTERCEPTORS_ONLY_IN_INVOKE,
            RogueInterceptor.class),
        violation(
            "REPOSITORY_INTERFACES_IN_REPOSITORY_PACKAGES",
            ArchitectureRules.REPOSITORY_INTERFACES_IN_REPOSITORY_PACKAGES,
            MisplacedRepository.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("violations")
  void ruleRejectsItsViolatingFixture(String name, ArchRule rule, List<Class<?>> fixtures) {
    JavaClasses classes = new ClassFileImporter().importClasses(fixtures);
    assertThatThrownBy(() -> rule.check(classes)).as(name).isInstanceOf(AssertionError.class);
  }

  @Test
  void everyRuleHasANegativeTest() {
    Set<ArchRule> covered = new HashSet<>();
    violations().forEach(a -> covered.add((ArchRule) a.get()[1]));
    assertThat(covered).containsExactlyInAnyOrderElementsOf(ArchitectureRules.ALL);
  }

  @Test
  void compliantFixturePassesEveryRule() {
    JavaClasses classes = new ClassFileImporter().importClasses(CompliantService.class);
    ArchitectureRules.ALL.forEach(rule -> rule.check(classes));
  }

  private static Arguments violation(String name, ArchRule rule, Class<?>... fixtures) {
    return Arguments.of(name, rule, List.of(fixtures));
  }
}
