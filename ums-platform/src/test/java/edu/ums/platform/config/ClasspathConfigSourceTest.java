package edu.ums.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClasspathConfigSourceTest {

  private final ClasspathConfigSource source =
      new ClasspathConfigSource(ClasspathConfigSourceTest.class.getClassLoader());

  @Test
  void loadsExistingResourceAsUtf8() {
    Map<String, String> values = source.load("config-test/sample.properties").orElseThrow();
    assertThat(values)
        .containsEntry("ums.db.user", "ums_app")
        .containsEntry("ums.sample.unicode", "Café")
        .hasSize(3);
  }

  @Test
  void missingResourceIsEmpty() {
    assertThat(source.load("config-test/does-not-exist.properties")).isEmpty();
  }

  @Test
  void loadedMapIsUnmodifiable() {
    Map<String, String> values = source.load("config-test/sample.properties").orElseThrow();
    assertThat(values).isUnmodifiable();
  }
}
