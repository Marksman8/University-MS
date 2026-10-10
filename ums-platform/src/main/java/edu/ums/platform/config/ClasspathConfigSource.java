package edu.ums.platform.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Reads properties resources (UTF-8) from a class loader. */
public final class ClasspathConfigSource implements ConfigSource {

  private final ClassLoader classLoader;

  /** Reads from {@code classLoader}. */
  public ClasspathConfigSource(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public Optional<Map<String, String>> load(String resourceName) {
    try (InputStream in = classLoader.getResourceAsStream(resourceName)) {
      if (in == null) {
        return Optional.empty();
      }
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        Properties properties = new Properties();
        properties.load(reader);
        Map<String, String> values = new HashMap<>();
        properties.stringPropertyNames().forEach(k -> values.put(k, properties.getProperty(k)));
        return Optional.of(Map.copyOf(values));
      }
    } catch (IOException e) {
      throw new ConfigException(List.of(resourceName + ": cannot be read"), e);
    }
  }
}
