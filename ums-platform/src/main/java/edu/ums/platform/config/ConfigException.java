package edu.ums.platform.config;

import java.util.List;

/**
 * Configuration is missing or invalid. Lists every problem at once. Messages name keys and, for
 * non-secrets, the rejected value; secret values are never included.
 */
public final class ConfigException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<String> problems;

  /** Creates an exception listing {@code problems} (copied). */
  public ConfigException(List<String> problems) {
    super("Invalid configuration:\n  - " + String.join("\n  - ", problems));
    this.problems = List.copyOf(problems);
  }

  /** Returns every problem found, unmodifiable. */
  public List<String> problems() {
    return problems == null ? List.of() : problems;
  }
}
