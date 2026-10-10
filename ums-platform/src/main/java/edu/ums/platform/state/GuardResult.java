package edu.ums.platform.state;

import edu.ums.api.error.ErrorCode;
import java.util.Map;
import java.util.Objects;

/** Outcome of a {@link Guard}: the transition may proceed, or it is refused with a reason. */
public sealed interface GuardResult {

  /** Returns the shared "allowed" result. */
  static GuardResult pass() {
    return Pass.INSTANCE;
  }

  /** Returns a refusal with {@code code} and user-safe {@code details}. */
  static GuardResult fail(ErrorCode code, Map<String, Object> details) {
    return new Fail(code, details);
  }

  /** Returns a refusal with {@code code} and no details. */
  static GuardResult fail(ErrorCode code) {
    return new Fail(code, Map.of());
  }

  /** The transition may proceed. */
  enum Pass implements GuardResult {
    INSTANCE
  }

  /**
   * The transition is refused.
   *
   * @param code error code reported by {@link edu.ums.platform.error.GuardFailedException}
   * @param details user-safe arguments, copied
   */
  record Fail(ErrorCode code, Map<String, Object> details) implements GuardResult {

    /** Copies the details and rejects {@code null}s. */
    public Fail {
      Objects.requireNonNull(code, "code");
      details = Map.copyOf(details);
    }
  }
}
