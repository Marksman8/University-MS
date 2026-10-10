package edu.ums.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExceptionHierarchyTest {

  static Stream<Arguments> fixedCodes() {
    return Stream.of(
        Arguments.of(
            new ValidationException(Map.of()), ErrorCode.VALIDATION_FAILED, DomainException.class),
        Arguments.of(
            new InvalidTransitionException(Map.of()),
            ErrorCode.INVALID_TRANSITION,
            DomainException.class),
        Arguments.of(new NotFoundException(Map.of()), ErrorCode.NOT_FOUND, DomainException.class),
        Arguments.of(new StaleDataException(Map.of()), ErrorCode.STALE_DATA, DomainException.class),
        Arguments.of(
            new AccessDeniedException(Map.of()),
            ErrorCode.ACCESS_DENIED,
            SecurityViolationException.class),
        Arguments.of(
            new DataUnavailableException(null),
            ErrorCode.DB_UNAVAILABLE,
            InfrastructureException.class),
        Arguments.of(
            new BusyException(null), ErrorCode.BUSY_TRY_AGAIN, InfrastructureException.class),
        Arguments.of(
            new ConflictException(null),
            ErrorCode.CONFLICT_TRY_AGAIN,
            InfrastructureException.class));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("fixedCodes")
  void fixedCodeClassesCarryTheirCodeAndParent(UmsException e, ErrorCode code, Class<?> parent) {
    assertThat(e.code()).isEqualTo(code);
    assertThat(e).isInstanceOf(parent);
    assertThat(e.messageKey()).isEqualTo(code.messageKey());
    assertThat(e.getMessage()).isEqualTo(code.name());
  }

  @Test
  void callerChosenCodesAreRestrictedToTheirArea() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PolicyException(ErrorCode.SECTION_FULL, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InfrastructureException(ErrorCode.NOT_FOUND, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuleViolationException(ErrorCode.TIMEOUT, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new GuardFailedException(ErrorCode.RATE_LIMITED, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AuthenticationException(ErrorCode.ACCESS_DENIED));
  }

  @Test
  void allowedCallerChosenCodesWork() {
    assertThat(new PolicyException(ErrorCode.CONFIRMATION_REQUIRED, Map.of()).code())
        .isEqualTo(ErrorCode.CONFIRMATION_REQUIRED);
    assertThat(new RuleViolationException(ErrorCode.LAST_ADMIN, Map.of()).code())
        .isEqualTo(ErrorCode.LAST_ADMIN);
    assertThat(new GuardFailedException(ErrorCode.MARKS_INCOMPLETE, Map.of()).code())
        .isEqualTo(ErrorCode.MARKS_INCOMPLETE);
    assertThat(new AuthenticationException(ErrorCode.ACCOUNT_LOCKED).details()).isEmpty();
    assertThat(new InfrastructureException(ErrorCode.TIMEOUT, null).code())
        .isEqualTo(ErrorCode.TIMEOUT);
  }

  @Test
  void detailsAreCopiedAndUnmodifiable() {
    Map<String, Object> source = new HashMap<>(Map.of("field", "size"));
    ValidationException e = new ValidationException(source);
    source.put("field", "changed");

    assertThat(e.details()).containsExactly(Map.entry("field", "size"));
    assertThatThrownBy(() -> e.details().put("x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void incidentIdIsEightBase32CharactersAndDeterministicPerSeed() {
    IncidentId a = IncidentId.random(new Random(1));
    IncidentId b = IncidentId.random(new Random(1));
    assertThat(a.value()).matches("[A-Z2-7]{8}");
    assertThat(a).isEqualTo(b);
    assertThatIllegalArgumentException().isThrownBy(() -> new IncidentId("abc"));
    assertThatIllegalArgumentException().isThrownBy(() -> new IncidentId("ABCDEFG1"));
  }

  @Test
  void standardConstraintMapRegistersEveryMasterConstraint() {
    assertThat(ConstraintErrorMap.standard().asMap())
        .containsKeys(
            "uq_enrolment_live",
            "ex_room_slot",
            "ex_faculty_slot",
            "ck_section_capacity",
            "uq_one_active_term",
            "uq_result_current",
            "ck_mark_nonneg")
        .containsEntry("ex_room_slot", ErrorCode.SLOT_CLASH);
    assertThat(ConstraintErrorMap.standard().lookup(null)).isEmpty();
  }
}
