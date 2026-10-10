package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.ErrorCode.Area;
import java.util.EnumSet;
import java.util.Map;

/**
 * An operational policy blocked the call: {@code FEATURE_DISABLED}, {@code RATE_LIMITED} or {@code
 * CONFIRMATION_REQUIRED}.
 */
public final class PolicyException extends UmsException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @throws IllegalArgumentException if {@code code} is not a policy code
   */
  public PolicyException(ErrorCode code, Map<String, Object> details) {
    super(requireArea(code, EnumSet.of(Area.POLICY)), details, null);
  }
}
