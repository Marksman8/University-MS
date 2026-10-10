package edu.ums.api;

import java.util.Objects;

/**
 * The signed-in user as seen by the frontend.
 *
 * @param username login name
 * @param displayName name to show in the UI
 * @param role the user's role
 */
public record UserSummary(String username, String displayName, Role role) {

  /** Rejects {@code null} components. */
  public UserSummary {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(role, "role");
  }
}
