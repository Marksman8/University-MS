package edu.ums.proto.auth.service;

import edu.ums.api.Role;
import edu.ums.api.error.ErrorCode;
import edu.ums.api.proto.UserRow;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.AuthenticationException;
import edu.ums.platform.error.ValidationException;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.auth.domain.LoginOutcome;
import edu.ums.proto.auth.domain.LoginRow;
import edu.ums.proto.auth.repository.ProtoUserRepository;
import edu.ums.proto.shared.Actor;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Login with uniform BAD_CREDENTIALS and lockout (one conditional update under a row lock), logout,
 * and admin user creation.
 */
// Complexity: time O(2^cost) per login (BCrypt) + O(log n) index lookups; space O(1)
public final class DefaultAuthService implements AuthService {

  private static final int MAX_USERNAME = 64;

  private final ProtoUserRepository users;
  private final ActivityRecorder activity;
  private final PasswordHasher hasher;
  private final TransactionRunner transactions;
  private final int lockoutThreshold;
  private final Duration lockoutDuration;

  /** Creates the service. */
  public DefaultAuthService(
      ProtoUserRepository users,
      ActivityRecorder activity,
      PasswordHasher hasher,
      TransactionRunner transactions,
      int lockoutThreshold,
      Duration lockoutDuration) {
    this.users = Objects.requireNonNull(users, "users");
    this.activity = Objects.requireNonNull(activity, "activity");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.lockoutThreshold = lockoutThreshold;
    this.lockoutDuration = Objects.requireNonNull(lockoutDuration, "lockoutDuration");
  }

  @Override
  public Actor login(String username, char[] password) {
    String normalised = normalise(username);
    if (normalised == null) {
      hasher.verifyDummy(password);
      activity.failure(null, username, "LOGIN_FAILED", Map.of("reason", "BAD_CREDENTIALS"));
      throw new AuthenticationException(ErrorCode.BAD_CREDENTIALS);
    }
    LoginOutcome outcome = transactions.inTransaction(false, () -> attempt(normalised, password));
    if (outcome instanceof LoginOutcome.Failure failure) {
      throw new AuthenticationException(failure.code());
    }
    return ((LoginOutcome.Success) outcome).actor();
  }

  /** The login transaction: always returns normally so the counter and audit rows commit. */
  private LoginOutcome attempt(String username, char[] password) {
    Optional<LoginRow> found = users.lockForLogin(username);
    if (found.isEmpty() || !found.get().active()) {
      hasher.verifyDummy(password);
      return refuse(null, username, ErrorCode.BAD_CREDENTIALS, "BAD_CREDENTIALS", false);
    }
    LoginRow row = found.get();
    Actor actor =
        new Actor(row.id(), row.username(), row.fullName(), row.role(), UUID.randomUUID());
    if (row.lockedNow()) {
      java.util.Arrays.fill(password, '\0');
      return refuse(actor, username, ErrorCode.ACCOUNT_LOCKED, "LOCKED", false);
    }
    if (!hasher.verify(password, row.passwordHash())) {
      boolean lockedNow = users.recordFailure(row.id(), lockoutThreshold, lockoutDuration);
      if (lockedNow) {
        activity.success(actor, "ACCOUNT_LOCKED", "app_user", row.id(), Map.of());
        return refuse(actor, username, ErrorCode.ACCOUNT_LOCKED, "BAD_CREDENTIALS", true);
      }
      return refuse(actor, username, ErrorCode.BAD_CREDENTIALS, "BAD_CREDENTIALS", false);
    }
    users.resetFailures(row.id());
    activity.success(actor, "LOGIN", "app_user", row.id(), Map.of());
    return new LoginOutcome.Success(actor);
  }

  /** Writes LOGIN_FAILED in the same (committing) transaction. */
  private LoginOutcome refuse(
      Actor actor, String username, ErrorCode code, String reason, boolean lockedNow) {
    activity.failure(actor, username, "LOGIN_FAILED", Map.of("reason", reason)); // joins this tx
    return new LoginOutcome.Failure(code, reason, lockedNow);
  }

  @Override
  public void logout(Actor actor) {
    activity.success(actor, "LOGOUT", "app_user", actor.userId(), Map.of());
  }

  @Override
  public String createUser(Actor admin, Role role, String username, String fullName) {
    String normalised = normalise(username);
    if (normalised == null || fullName == null || fullName.isBlank() || fullName.length() > 128) {
      throw new ValidationException(Map.of("field", normalised == null ? "username" : "fullName"));
    }
    char[] temporary = hasher.temporaryPassword();
    String shown = new String(temporary);
    long id = users.insert(normalised, hasher.hash(temporary), role, fullName.strip());
    activity.success(
        admin, "USER_CREATED", "app_user", id, Map.of("username", normalised, "role", role));
    return shown;
  }

  @Override
  public List<UserRow> users() {
    return users.list(200);
  }

  /** Trim + lower-case; 3..64 chars of [a-z0-9._-] checked with a char loop; else null. */
  static String normalise(String raw) {
    if (raw == null || raw.length() > 256) {
      return null;
    }
    String value = raw.strip().toLowerCase(Locale.ROOT);
    if (value.length() < 3 || value.length() > MAX_USERNAME) {
      return null;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
      if (!ok) {
        return null;
      }
    }
    return value;
  }
}
