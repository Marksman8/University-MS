package edu.ums.api.dto;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import java.util.Map;

/**
 * Opaque keyset paging token. Callers pass it back unchanged; only the backend decodes it.
 *
 * @param token non-blank, at most {@value #MAX_LENGTH} characters
 */
public record Cursor(String token) {

  /** Maximum token length. */
  public static final int MAX_LENGTH = 512;

  /**
   * Validates the token.
   *
   * @throws UmsApiException {@code VALIDATION_FAILED} if blank or too long
   */
  public Cursor {
    if (token == null || token.isBlank()) {
      throw invalid("blank");
    }
    if (token.length() > MAX_LENGTH) {
      throw invalid("too_long");
    }
  }

  @Override
  public String toString() {
    return "Cursor[" + token.length() + " chars]";
  }

  private static UmsApiException invalid(String reason) {
    return new UmsApiException(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.VALIDATION_FAILED.messageKey(),
        Map.of("field", "cursor", "reason", reason));
  }
}
