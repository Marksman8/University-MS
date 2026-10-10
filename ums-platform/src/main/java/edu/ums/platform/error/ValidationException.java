package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/** Input is malformed or out of range ({@code VALIDATION_FAILED}). */
public final class ValidationException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception; {@code details} name the field and the rule. */
  public ValidationException(Map<String, Object> details) {
    super(ErrorCode.VALIDATION_FAILED, details, null);
  }

  /** Creates the exception with an underlying cause. */
  public ValidationException(Map<String, Object> details, Throwable cause) {
    super(ErrorCode.VALIDATION_FAILED, details, cause);
  }
}
