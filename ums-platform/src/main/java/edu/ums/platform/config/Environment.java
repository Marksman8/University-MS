package edu.ums.platform.config;

import java.util.Locale;
import java.util.Optional;

/** Deployment environment, selected by the {@code UMS_ENV} variable. */
public enum Environment {
  LOCAL,
  TEST,
  STAGING,
  PROD;

  /**
   * Returns the environment-specific properties file name, e.g. {@code
   * application-test.properties}.
   */
  public String profileResource() {
    return "application-" + name().toLowerCase(Locale.ROOT) + ".properties";
  }

  /** Parses a case-insensitive name; empty if it is not a known environment. */
  public static Optional<Environment> parse(String value) {
    for (Environment candidate : values()) {
      if (candidate.name().equalsIgnoreCase(value.trim())) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
