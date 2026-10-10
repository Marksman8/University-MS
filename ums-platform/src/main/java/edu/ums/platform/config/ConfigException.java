package edu.ums.platform.config;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.InfrastructureException;
import java.util.List;

/**
 * Configuration is missing or invalid ({@code INTERNAL_ERROR}; raised only at startup). Lists every
 * problem at once. Messages name keys and, for non-secrets, the rejected value; secret values are
 * never included.
 */
public final class ConfigException extends InfrastructureException {

  private static final long serialVersionUID = 1L;

  private final transient List<String> problems;

  /** Creates an exception listing {@code problems} (copied). */
  public ConfigException(List<String> problems) {
    this(problems, null);
  }

  /** Creates an exception listing {@code problems} (copied) with an underlying cause. */
  public ConfigException(List<String> problems, Throwable cause) {
    super(
        ErrorCode.INTERNAL_ERROR,
        cause,
        "Invalid configuration:\n  - " + String.join("\n  - ", problems));
    this.problems = List.copyOf(problems);
  }

  /** Returns every problem found, unmodifiable. */
  public List<String> problems() {
    return problems == null ? List.of() : problems;
  }
}
