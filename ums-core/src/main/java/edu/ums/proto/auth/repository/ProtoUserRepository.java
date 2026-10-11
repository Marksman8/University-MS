package edu.ums.proto.auth.repository;

import edu.ums.api.Role;
import edu.ums.api.proto.UserRow;
import edu.ums.proto.auth.domain.LoginRow;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Prototype persistence of {@code app_user}. Runs in the caller's transaction. */
public interface ProtoUserRepository {

  /** Reads the user for login and locks the row ({@code FOR UPDATE}). */
  Optional<LoginRow> lockForLogin(String username);

  /**
   * Counts a failed attempt with one conditional update.
   *
   * @return {@code true} if this attempt set the lock
   */
  boolean recordFailure(long userId, int threshold, Duration lockout);

  /** Clears the failure counter and any lock. */
  void resetFailures(long userId);

  /** Inserts a user and returns its id (USERNAME_TAKEN on a duplicate). */
  long insert(String username, String passwordHash, Role role, String fullName);

  /** Up to {@code limit} users ordered by username. */
  List<UserRow> list(int limit);

  /** Whether any user exists. */
  boolean any();
}
