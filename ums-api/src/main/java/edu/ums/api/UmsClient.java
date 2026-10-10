package edu.ums.api;

import edu.ums.api.error.UmsApiException;

/** Entry point for the frontend. Thread-safe; calls block and must not run on the JavaFX thread. */
public interface UmsClient {

  /**
   * Signs a user in.
   *
   * @param password zeroed by the backend before this method returns
   * @return an open session; close it to log out
   * @throws UmsApiException {@code BAD_CREDENTIALS}, {@code ACCOUNT_LOCKED}, {@code RATE_LIMITED},
   *     {@code DB_UNAVAILABLE}
   */
  Session login(String username, char[] password);
}
