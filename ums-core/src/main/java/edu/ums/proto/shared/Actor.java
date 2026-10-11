package edu.ums.proto.shared;

import edu.ums.api.Role;
import java.util.Objects;
import java.util.UUID;

/**
 * The signed-in user on whose behalf a prototype facade call runs. Built once at login; student and
 * faculty operations take their user id from here, never from a parameter.
 */
public record Actor(long userId, String username, String fullName, Role role, UUID sessionId) {

  /** Rejects {@code null} components. */
  public Actor {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(fullName, "fullName");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(sessionId, "sessionId");
  }
}
