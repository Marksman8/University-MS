package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/** A state machine has no transition between the requested states ({@code INVALID_TRANSITION}). */
public final class InvalidTransitionException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception; {@code details} normally carry {@code from} and {@code to}. */
  public InvalidTransitionException(Map<String, Object> details) {
    super(ErrorCode.INVALID_TRANSITION, details, null);
  }
}
