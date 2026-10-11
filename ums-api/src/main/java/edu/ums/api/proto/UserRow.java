package edu.ums.api.proto;

import edu.ums.api.Role;

/** A user account as listed to the admin (prototype). */
public record UserRow(long id, String username, String fullName, Role role, boolean active) {}
