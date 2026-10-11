package edu.ums.proto.auth.domain;

import edu.ums.api.Role;

/**
 * A user row read (and row-locked) for login. {@code lockedNow} is evaluated by the database
 * ({@code locked_until > now()}). {@link #toString()} never prints the hash.
 */
public record LoginRow(
    long id,
    String username,
    String fullName,
    Role role,
    boolean active,
    String passwordHash,
    boolean lockedNow) {

  @Override
  public String toString() {
    return "LoginRow[id=" + id + ", username=" + username + ", passwordHash=****]";
  }
}
