package edu.ums.platform.config;

import java.util.Map;
import java.util.Optional;

/** Supplies the contents of a named properties resource. */
@FunctionalInterface
public interface ConfigSource {

  /**
   * Loads a properties resource.
   *
   * @param resourceName e.g. {@code application.properties}
   * @return the key/value pairs, or empty if the resource does not exist
   * @throws ConfigException if the resource exists but cannot be read
   */
  Optional<Map<String, String>> load(String resourceName);
}
