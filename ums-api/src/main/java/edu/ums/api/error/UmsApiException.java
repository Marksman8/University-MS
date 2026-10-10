package edu.ums.api.error;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The only exception that crosses the facade boundary. Carries a typed {@link ErrorCode}, its
 * message key and safe message arguments. {@link #getMessage()} never contains SQL, stack details,
 * secrets or other users' data: it is the code name plus, for {@code INTERNAL_ERROR}, the incident
 * id.
 */
public final class UmsApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final String messageKey;
  private final transient Map<String, Object> args;
  private final String incidentId;

  /**
   * Creates an exception without an incident id.
   *
   * @param args message arguments; copied, must not contain {@code null} keys or values
   */
  public UmsApiException(ErrorCode code, String messageKey, Map<String, Object> args) {
    this(code, messageKey, args, null);
  }

  /**
   * Creates an exception.
   *
   * @param args message arguments; copied, must not contain {@code null} keys or values
   * @param incidentId correlation id for {@code INTERNAL_ERROR}, or {@code null}
   */
  public UmsApiException(
      ErrorCode code, String messageKey, Map<String, Object> args, String incidentId) {
    super(buildMessage(code, incidentId));
    this.code = Objects.requireNonNull(code, "code");
    this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
    this.args = Map.copyOf(Objects.requireNonNull(args, "args"));
    this.incidentId = incidentId;
  }

  /** Creates an exception for {@code code} using its default message key and no arguments. */
  public static UmsApiException of(ErrorCode code) {
    return new UmsApiException(code, code.messageKey(), Map.of());
  }

  public ErrorCode code() {
    return code;
  }

  public String messageKey() {
    return messageKey;
  }

  /** Returns the message arguments as an unmodifiable map. */
  public Map<String, Object> args() {
    return args == null ? Map.of() : args;
  }

  /** Returns the incident id, present only for {@code INTERNAL_ERROR}. */
  public Optional<String> incidentId() {
    return Optional.ofNullable(incidentId);
  }

  private static String buildMessage(ErrorCode code, String incidentId) {
    String name = code == null ? "null" : code.name();
    return incidentId == null ? name : name + " (incident " + incidentId + ")";
  }
}
