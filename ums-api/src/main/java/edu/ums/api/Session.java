package edu.ums.api;

import edu.ums.api.error.UmsApiException;
import edu.ums.api.listener.NoticeListener;

/**
 * A signed-in user's session. Thread-safe. Every method other than {@link #close()} throws {@code
 * SESSION_EXPIRED} once the session has expired or been closed.
 */
public interface Session extends AutoCloseable {

  /** Returns the signed-in user. */
  UserSummary user();

  /** Returns the signed-in user's role. */
  Role role();

  /** Returns {@code true} if the password was reset and must be changed before other calls. */
  boolean mustChangePassword();

  /**
   * Changes the signed-in user's password. Both arrays are zeroed before the method returns.
   *
   * @throws UmsApiException {@code BAD_CREDENTIALS}, {@code PASSWORD_POLICY}
   */
  void changePassword(char[] current, char[] next);

  /**
   * Returns the student facade.
   *
   * @throws UmsApiException {@code ACCESS_DENIED} if the role is not {@link Role#STUDENT}
   */
  StudentApi student();

  /**
   * Returns the faculty facade.
   *
   * @throws UmsApiException {@code ACCESS_DENIED} if the role is not {@link Role#FACULTY}
   */
  FacultyApi faculty();

  /**
   * Returns the admin facade.
   *
   * @throws UmsApiException {@code ACCESS_DENIED} if the role is not {@link Role#ADMIN}
   */
  AdminApi admin();

  /**
   * Returns the developer facade.
   *
   * @throws UmsApiException {@code ACCESS_DENIED} if the role is not {@link Role#DEV}
   */
  DevApi dev();

  /**
   * Registers a listener for notices visible to this user. Callbacks arrive on backend threads.
   *
   * @return a handle to stop delivery; also stopped automatically when the session closes
   */
  Subscription onNotice(NoticeListener listener);

  /** Logs out. Idempotent; never throws. */
  @Override
  void close();
}
