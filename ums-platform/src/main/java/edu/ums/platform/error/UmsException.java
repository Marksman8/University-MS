package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Root of every backend exception. Carries an {@link ErrorCode} and {@code details}: message
 * arguments that are safe to show to the user. Technical context (SQL state, driver messages) stays
 * in the cause and never goes into {@code details} or {@link #getMessage()}.
 */
public abstract class UmsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final transient Map<String, Object> details;

  /**
   * Creates an exception whose message is the code name.
   *
   * @param details user-safe message arguments; copied, no {@code null} keys or values
   * @param cause underlying failure, or {@code null}
   */
  protected UmsException(ErrorCode code, Map<String, Object> details, Throwable cause) {
    this(new Parts(code, details, null), cause);
  }

  /** Creates an exception with an explicit message; the message must not contain SQL or secrets. */
  protected UmsException(
      ErrorCode code, Map<String, Object> details, Throwable cause, String message) {
    this(new Parts(code, details, Objects.requireNonNull(message, "message")), cause);
  }

  /**
   * All validation happens in {@link Parts}, before {@code super()}, so no half-built object
   * escapes.
   */
  private UmsException(Parts parts, Throwable cause) {
    super(parts.message(), cause);
    this.code = parts.code();
    this.details = parts.details();
  }

  public final ErrorCode code() {
    return code;
  }

  /** Returns the i18n key of {@link #code()}. */
  public final String messageKey() {
    return code.messageKey();
  }

  /** Returns the user-safe message arguments, unmodifiable. */
  public final Map<String, Object> details() {
    return details == null ? Map.of() : details;
  }

  /**
   * Throws {@link IllegalArgumentException} unless {@code code} belongs to one of {@code areas}.
   * Used by subclasses that accept a caller-chosen code.
   */
  protected static ErrorCode requireArea(ErrorCode code, Set<ErrorCode.Area> areas) {
    // Called inside subclass super(...) arguments, i.e. before any object state exists.
    Objects.requireNonNull(code, "code");
    if (!areas.contains(code.area())) {
      throw new IllegalArgumentException(code + " is not allowed here; expected area " + areas);
    }
    return code;
  }

  /** Validated constructor arguments; {@code message == null} means "use the code name". */
  private record Parts(ErrorCode code, Map<String, Object> details, String message) {

    Parts {
      Objects.requireNonNull(code, "code");
      details = Map.copyOf(Objects.requireNonNull(details, "details"));
      message = message == null ? code.name() : message;
    }
  }
}
