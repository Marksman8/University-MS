package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/** A request was understood but violates a business rule, state rule or data constraint. */
public abstract class DomainException extends UmsException {

  private static final long serialVersionUID = 1L;

  protected DomainException(ErrorCode code, Map<String, Object> details, Throwable cause) {
    super(code, details, cause);
  }
}
