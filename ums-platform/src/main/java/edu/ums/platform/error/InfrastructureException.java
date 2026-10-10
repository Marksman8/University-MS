package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.ErrorCode.Area;
import java.util.EnumSet;
import java.util.Map;

/**
 * A technical failure outside the caller's control. Used directly for {@code TIMEOUT}, {@code
 * CANCELLED} and {@code INTERNAL_ERROR}; subclasses cover the retryable/transient cases.
 */
public class InfrastructureException extends UmsException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @throws IllegalArgumentException if {@code code} is not an infrastructure code
   */
  public InfrastructureException(ErrorCode code, Throwable cause) {
    super(requireArea(code, EnumSet.of(Area.INFRA)), Map.of(), cause);
  }

  /** Creates the exception with an explicit message (startup errors only; no SQL or secrets). */
  protected InfrastructureException(ErrorCode code, Throwable cause, String message) {
    super(requireArea(code, EnumSet.of(Area.INFRA)), Map.of(), cause, message);
  }
}
