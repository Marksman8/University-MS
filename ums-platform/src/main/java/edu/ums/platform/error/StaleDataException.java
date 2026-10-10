package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;

/**
 * A compare-and-set update matched no row because the version or state changed ({@code
 * STALE_DATA}).
 */
public final class StaleDataException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception; {@code details} normally carry the entity type. */
  public StaleDataException(Map<String, Object> details) {
    super(ErrorCode.STALE_DATA, details, null);
  }

  /** Creates the exception with an underlying cause. */
  public StaleDataException(Map<String, Object> details, Throwable cause) {
    super(ErrorCode.STALE_DATA, details, cause);
  }
}
