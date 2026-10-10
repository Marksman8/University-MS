package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/** Authentication or authorisation failed. */
public abstract class SecurityViolationException extends UmsException {

  private static final long serialVersionUID = 1L;

  protected SecurityViolationException(ErrorCode code, Map<String, Object> details) {
    super(code, details, null);
  }
}
