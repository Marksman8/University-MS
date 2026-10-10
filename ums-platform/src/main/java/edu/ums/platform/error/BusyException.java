package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;

/**
 * A resource is saturated: pool wait timed out, lock wait timed out or a bounded queue is full
 * ({@code BUSY_TRY_AGAIN}).
 */
public final class BusyException extends InfrastructureException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public BusyException(Throwable cause) {
    super(ErrorCode.BUSY_TRY_AGAIN, cause);
  }
}
