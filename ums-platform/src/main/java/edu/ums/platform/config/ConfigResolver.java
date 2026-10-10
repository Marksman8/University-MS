package edu.ums.platform.config;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves a key against three layers, highest precedence first: environment variables, then the
 * environment-specific properties file, then {@code application.properties}. Blank values count as
 * absent. Secrets are looked up in the environment only.
 */
// Complexity: time O(1) per resolve (three HashMap lookups); space O(p + e) — the copied layers
public final class ConfigResolver {

  private final Map<String, String> base;
  private final Map<String, String> profile;
  private final Map<String, String> environment;

  /** Copies the three layers. */
  public ConfigResolver(
      Map<String, String> base, Map<String, String> profile, Map<String, String> environment) {
    this.base = Map.copyOf(base);
    this.profile = Map.copyOf(profile);
    this.environment = Map.copyOf(environment);
  }

  /** Returns the effective value of {@code key}, or empty if no layer supplies a non-blank one. */
  public Optional<String> resolve(ConfigKey key) {
    Optional<String> fromEnv = nonBlank(environment.get(key.envVar()));
    if (fromEnv.isPresent() || key.isSecret()) {
      return fromEnv;
    }
    String property = key.property().orElseThrow();
    return nonBlank(profile.get(property)).or(() -> nonBlank(base.get(property)));
  }

  private static Optional<String> nonBlank(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
  }
}
