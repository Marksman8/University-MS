package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The caller could not be authenticated: {@code BAD_CREDENTIALS}, {@code ACCOUNT_LOCKED} or {@code
 * SESSION_EXPIRED}. Details never reveal which part of a credential was wrong.
 */
public final class AuthenticationException extends SecurityViolationException {

  private static final long serialVersionUID = 1L;

  private static final Set<ErrorCode> ALLOWED =
      EnumSet.of(ErrorCode.BAD_CREDENTIALS, ErrorCode.ACCOUNT_LOCKED, ErrorCode.SESSION_EXPIRED);

  /**
   * Creates the exception.
   *
   * @throws IllegalArgumentException if {@code code} is not an authentication code
   */
  public AuthenticationException(ErrorCode code) {
    super(requireAuthCode(code), Map.of());
  }

  private static ErrorCode requireAuthCode(ErrorCode code) {
    if (!ALLOWED.contains(code)) {
      throw new IllegalArgumentException(code + " is not an authentication code");
    }
    return code;
  }
}
