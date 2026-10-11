package edu.ums.proto.auth.domain;

import edu.ums.api.error.ErrorCode;
import edu.ums.proto.shared.Actor;

/**
 * Result of the login transaction. The transaction always commits (so a failed-attempt counter
 * survives); the caller throws for {@link Failure} only after the commit.
 */
public sealed interface LoginOutcome {

  /** Signed in. */
  record Success(Actor actor) implements LoginOutcome {}

  /** Refused; {@code lockedNow} is true for the attempt that set the lock. */
  record Failure(ErrorCode code, String reason, boolean lockedNow) implements LoginOutcome {}
}
