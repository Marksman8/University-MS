package edu.ums.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UmsApiExceptionTest {

  @Test
  void argsAreCopiedAndUnmodifiable() {
    Map<String, Object> source = new HashMap<>();
    source.put("field", "size");
    UmsApiException e =
        new UmsApiException(ErrorCode.VALIDATION_FAILED, "error.domain.validation_failed", source);

    source.put("field", "changed");
    source.put("extra", 1);

    assertThat(e.args()).containsExactly(Map.entry("field", "size"));
    assertThatThrownBy(() -> e.args().put("x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullArgsValuesAreRejected() {
    Map<String, Object> source = new HashMap<>();
    source.put("field", null);
    assertThatNullPointerException()
        .isThrownBy(() -> new UmsApiException(ErrorCode.NOT_FOUND, "k", source));
  }

  @Test
  void messageIsCodeOnlyAndNeverContainsArgs() {
    UmsApiException e =
        new UmsApiException(
            ErrorCode.NOT_FOUND, "error.domain.not_found", Map.of("detail", "SELECT secret"));
    assertThat(e.getMessage()).isEqualTo("NOT_FOUND").doesNotContain("SELECT");
    assertThat(e.incidentId()).isEmpty();
  }

  @Test
  void internalErrorCarriesIncidentId() {
    UmsApiException e =
        new UmsApiException(
            ErrorCode.INTERNAL_ERROR, "error.infra.internal_error", Map.of(), "AB12CD34");
    assertThat(e.incidentId()).contains("AB12CD34");
    assertThat(e.getMessage()).isEqualTo("INTERNAL_ERROR (incident AB12CD34)");
  }

  @Test
  void ofUsesDefaultKey() {
    UmsApiException e = UmsApiException.of(ErrorCode.SECTION_FULL);
    assertThat(e.code()).isEqualTo(ErrorCode.SECTION_FULL);
    assertThat(e.messageKey()).isEqualTo("error.reg.section_full");
    assertThat(e.args()).isEmpty();
  }
}
