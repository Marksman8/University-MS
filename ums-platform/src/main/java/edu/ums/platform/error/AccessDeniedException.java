package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/** The authenticated caller lacks the role or ownership required ({@code ACCESS_DENIED}). */
public final class AccessDeniedException extends SecurityViolationException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception; {@code details} must not reveal whether the target exists. */
  public AccessDeniedException(Map<String, Object> details) {
    super(ErrorCode.ACCESS_DENIED, details);
  }
}
