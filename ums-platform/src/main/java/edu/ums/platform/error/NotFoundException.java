package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/** A referenced record does not exist ({@code NOT_FOUND}). */
public final class NotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception; {@code details} normally carry the entity type. */
  public NotFoundException(Map<String, Object> details) {
    super(ErrorCode.NOT_FOUND, details, null);
  }

  /** Creates the exception with an underlying cause (e.g. a foreign-key violation). */
  public NotFoundException(Map<String, Object> details, Throwable cause) {
    super(ErrorCode.NOT_FOUND, details, cause);
  }
}
