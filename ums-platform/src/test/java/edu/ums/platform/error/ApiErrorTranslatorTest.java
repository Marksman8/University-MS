package edu.ums.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiErrorTranslatorTest {

  private final ApiErrorTranslator translator = new ApiErrorTranslator(new Random(42));

  @Test
  void copiesCodeKeyAndSafeArgs() {
    UUID id = UUID.randomUUID();
    Instant when = Instant.parse("2026-01-01T00:00:00Z");
    RuleViolationException e =
        new RuleViolationException(
            ErrorCode.CREDIT_LIMIT,
            Map.of("limit", 26, "attempted", 28, "ref", id, "at", when, "flag", true));

    UmsApiException api = translator.translate(e);

    assertThat(api.code()).isEqualTo(ErrorCode.CREDIT_LIMIT);
    assertThat(api.messageKey()).isEqualTo("error.reg.credit_limit");
    assertThat(api.args())
        .containsOnly(
            Map.entry("limit", 26),
            Map.entry("attempted", 28),
            Map.entry("ref", id),
            Map.entry("at", when),
            Map.entry("flag", true));
    assertThat(api.incidentId()).isEmpty();
  }

  @Test
  void dropsUnsafeArgs() {
    ValidationException e =
        new ValidationException(
            Map.of(
                "field",
                "email",
                "tooLong",
                "x".repeat(ApiErrorTranslator.MAX_STRING_ARG + 1),
                "object",
                new Object(),
                "bad name!",
                "value"));

    assertThat(translator.translate(e).args()).containsOnly(Map.entry("field", "email"));
  }

  @Test
  void internalErrorGetsIncidentId() {
    UmsApiException api =
        translator.translate(
            new InfrastructureException(ErrorCode.INTERNAL_ERROR, new IllegalStateException("x")));

    assertThat(api.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(api.incidentId()).hasValueSatisfying(v -> assertThat(v).matches("[A-Z2-7]{8}"));
    assertThat(api.args()).containsEntry("incidentId", api.incidentId().orElseThrow());
  }

  @Test
  void unknownThrowableBecomesInternalErrorWithoutLeakingItsMessage() {
    UmsApiException api = translator.translate(new IllegalStateException("password=hunter2"));

    assertThat(api.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(api.incidentId()).isPresent();
    assertThat(api.getMessage()).doesNotContain("hunter2");
  }

  @Test
  void apiExceptionPassesThroughUnchanged() {
    UmsApiException original = UmsApiException.of(ErrorCode.RATE_LIMITED);
    assertThat(translator.translate(original)).isSameAs(original);
  }

  @Test
  void otherInfrastructureCodesHaveNoIncidentId() {
    assertThat(translator.translate(new BusyException(null)).incidentId()).isEmpty();
    assertThat(translator.translate(new AuthenticationException(ErrorCode.BAD_CREDENTIALS)).code())
        .isEqualTo(ErrorCode.BAD_CREDENTIALS);
  }
}
