package edu.ums.platform.config;

import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads typed values and records a problem for each missing or invalid key instead of failing on
 * the first one. Typed getters return {@code null} for a bad key; callers must check {@link
 * #problems()} before using the values.
 */
// Complexity: time O(1) per read; space O(k) — at most one problem per ConfigKey
final class ConfigReader {

  private static final int MAX_ECHO = 60;

  private final ConfigResolver resolver;
  private final List<String> problems = new ArrayList<>(ConfigKey.values().length);

  ConfigReader(ConfigResolver resolver) {
    this.resolver = resolver;
  }

  /** Records an externally detected problem (e.g. an invalid {@code UMS_ENV}). */
  void addProblem(String problem) {
    problems.add(problem);
  }

  /** Returns every problem recorded so far. */
  List<String> problems() {
    return List.copyOf(problems);
  }

  String string(ConfigKey key) {
    return required(key).orElse(null);
  }

  String jdbcUrl(ConfigKey key) {
    Optional<String> value = required(key);
    if (value.isPresent() && !value.get().startsWith("jdbc:postgresql:")) {
      return invalid(key, value.get(), "must start with jdbc:postgresql:");
    }
    return value.orElse(null);
  }

  Integer intInRange(ConfigKey key, int min, int max) {
    Optional<String> value = required(key);
    if (value.isEmpty()) {
      return null;
    }
    try {
      int parsed = Integer.parseInt(value.get());
      if (parsed >= min && parsed <= max) {
        return parsed;
      }
    } catch (NumberFormatException notANumber) {
      // reported below with the same message as an out-of-range value
    }
    return invalid(key, value.get(), "must be an integer in " + min + ".." + max);
  }

  Duration positiveDuration(ConfigKey key) {
    Optional<String> value = required(key);
    if (value.isEmpty()) {
      return null;
    }
    try {
      Duration parsed = Duration.parse(value.get());
      if (!parsed.isNegative() && !parsed.isZero()) {
        return parsed;
      }
    } catch (DateTimeParseException notADuration) {
      // reported below with the same message as a non-positive value
    }
    return invalid(key, value.get(), "must be a positive ISO-8601 duration such as PT5S");
  }

  Path path(ConfigKey key) {
    return required(key).map(Path::of).orElse(null);
  }

  Secret secret(ConfigKey key) {
    return required(key).map(Secret::of).orElse(null);
  }

  private Optional<String> required(ConfigKey key) {
    Optional<String> value = resolver.resolve(key);
    if (value.isEmpty()) {
      problems.add(
          key.displayName() + ": required" + (key.isSecret() ? " (environment variable)" : ""));
    }
    return value;
  }

  private <T> T invalid(ConfigKey key, String value, String rule) {
    String shown = value.length() > MAX_ECHO ? value.substring(0, MAX_ECHO) + "..." : value;
    problems.add(key.displayName() + ": " + rule + " (was '" + shown + "')");
    return null;
  }
}
