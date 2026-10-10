package edu.ums.platform.error;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.api.id.EntityId;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts any throwable into the {@link UmsApiException} that crosses the facade boundary.
 *
 * <ul>
 *   <li>{@link UmsApiException}: returned unchanged.
 *   <li>{@link UmsException}: code and key copied; only safe arguments kept (strings of at most
 *       {@value #MAX_STRING_ARG} characters, numbers, booleans, enums, typed ids, UUIDs,
 *       temporals).
 *   <li>Anything else, and {@code INTERNAL_ERROR}: new incident id, logged at ERROR with the cause.
 * </ul>
 *
 * Log levels: internal ERROR; other infrastructure WARN; security INFO; domain/policy DEBUG. Log
 * lines carry the code and exception class, never driver messages or argument values.
 */
// Complexity: time O(a) per translate, a = number of detail arguments; space O(a)
public final class ApiErrorTranslator {

  static final int MAX_STRING_ARG = 256;

  private static final Logger LOG = LoggerFactory.getLogger(ApiErrorTranslator.class);
  private static final Pattern ARG_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

  private final RandomGenerator random;

  /** Creates a translator; {@code random} generates incident ids (use a SecureRandom). */
  public ApiErrorTranslator(RandomGenerator random) {
    this.random = Objects.requireNonNull(random, "random");
  }

  /** Translates {@code failure}; never returns {@code null}. */
  public UmsApiException translate(Throwable failure) {
    Objects.requireNonNull(failure, "failure");
    if (failure instanceof UmsApiException api) {
      return api;
    }
    if (failure instanceof UmsException ums && ums.code() != ErrorCode.INTERNAL_ERROR) {
      log(ums);
      return new UmsApiException(ums.code(), ums.messageKey(), safeArgs(ums.details()));
    }
    IncidentId incident = IncidentId.random(random);
    LOG.error(
        "INTERNAL_ERROR incident={} type={}", incident, failure.getClass().getName(), failure);
    return new UmsApiException(
        ErrorCode.INTERNAL_ERROR,
        ErrorCode.INTERNAL_ERROR.messageKey(),
        Map.of("incidentId", incident.value()),
        incident.value());
  }

  /** Keeps only arguments with well-formed names and safe, bounded value types. */
  static Map<String, Object> safeArgs(Map<String, Object> details) {
    Map<String, Object> safe = new HashMap<>();
    details.forEach(
        (name, value) -> {
          if (ARG_NAME.matcher(name).matches() && isSafe(value)) {
            safe.put(name, value);
          }
        });
    return safe;
  }

  private static boolean isSafe(Object value) {
    if (value instanceof CharSequence text) {
      return text.length() <= MAX_STRING_ARG;
    }
    return value instanceof Number
        || value instanceof Boolean
        || value instanceof Enum<?>
        || value instanceof EntityId
        || value instanceof UUID
        || value instanceof TemporalAccessor;
  }

  private static void log(UmsException e) {
    String cause = e.getCause() == null ? "-" : e.getCause().getClass().getName();
    if (e instanceof InfrastructureException) {
      LOG.warn("{} type={} cause={}", e.code(), e.getClass().getSimpleName(), cause);
    } else if (e instanceof SecurityViolationException) {
      LOG.info("{} type={}", e.code(), e.getClass().getSimpleName());
    } else {
      LOG.debug("{} type={}", e.code(), e.getClass().getSimpleName());
    }
  }
}
