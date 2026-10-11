package edu.ums.proto.auth.service;

import edu.ums.api.Role;
import edu.ums.api.proto.UserRow;
import edu.ums.proto.shared.Actor;
import java.util.List;

/** Prototype authentication and user administration. */
public interface AuthService {

  /**
   * Signs in; runs its own transaction, which always commits, and throws only afterwards.
   *
   * @throws edu.ums.platform.error.AuthenticationException BAD_CREDENTIALS or ACCOUNT_LOCKED
   */
  Actor login(String username, char[] password);

  /** Records a logout in the current transaction. */
  void logout(Actor actor);

  /** Creates a user (admin only, current transaction); returns the one-time temporary password. */
  String createUser(Actor admin, Role role, String username, String fullName);

  /** Up to 200 users ordered by username. */
  List<UserRow> users();
}
