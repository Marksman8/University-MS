package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;

/** The database cannot be reached ({@code DB_UNAVAILABLE}). */
public final class DataUnavailableException extends InfrastructureException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public DataUnavailableException(Throwable cause) {
    super(ErrorCode.DB_UNAVAILABLE, cause);
  }
}
