package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;

/**
 * A serialization failure or deadlock (SQLState 40001 / 40P01). Retried by the outermost
 * transaction; surfaces as {@code CONFLICT_TRY_AGAIN} once retries are exhausted.
 */
public final class ConflictException extends InfrastructureException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public ConflictException(Throwable cause) {
    super(ErrorCode.CONFLICT_TRY_AGAIN, cause);
  }
}
