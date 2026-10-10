package edu.ums.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ConfigResolverPropertiesTest {

  @Property
  void highestNonBlankLayerWins(
      @ForAll("nonSecretKeys") ConfigKey key,
      @ForAll("values") Optional<String> base,
      @ForAll("values") Optional<String> profile,
      @ForAll("values") Optional<String> env,
      @ForAll("noise") Map<String, String> noise) {
    ConfigResolver resolver =
        new ConfigResolver(
            layer(noise, key.property().orElseThrow(), base),
            layer(noise, key.property().orElseThrow(), profile),
            layer(noise, key.envVar(), env));

    Optional<String> expected =
        Stream.of(env, profile, base)
            .flatMap(Optional::stream)
            .filter(v -> !v.isBlank())
            .map(String::trim)
            .findFirst();
    assertThat(resolver.resolve(key)).isEqualTo(expected);
  }

  @Property
  void secretsComeFromEnvironmentOnly(
      @ForAll("secretKeys") ConfigKey key,
      @ForAll("values") Optional<String> fileValue,
      @ForAll("values") Optional<String> env) {
    ConfigResolver resolver =
        new ConfigResolver(
            layer(Map.of(), key.envVar(), fileValue),
            layer(Map.of(), key.envVar(), fileValue),
            layer(Map.of(), key.envVar(), env));

    assertThat(resolver.resolve(key)).isEqualTo(env.filter(v -> !v.isBlank()).map(String::trim));
  }

  @Provide
  Arbitrary<ConfigKey> nonSecretKeys() {
    return Arbitraries.of(Arrays.stream(ConfigKey.values()).filter(k -> !k.isSecret()).toList());
  }

  @Provide
  Arbitrary<ConfigKey> secretKeys() {
    return Arbitraries.of(Arrays.stream(ConfigKey.values()).filter(ConfigKey::isSecret).toList());
  }

  @Provide
  Arbitrary<Optional<String>> values() {
    Arbitrary<String> text =
        Arbitraries.oneOf(
            Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(12),
            Arbitraries.of("", " ", " padded "));
    return text.optional();
  }

  @Provide
  Arbitrary<Map<String, String>> noise() {
    return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> "noise." + s),
            Arbitraries.strings().alpha().ofMaxLength(8))
        .ofMaxSize(5);
  }

  private static Map<String, String> layer(
      Map<String, String> noise, String name, Optional<String> value) {
    Map<String, String> map = new HashMap<>(noise);
    value.ifPresent(v -> map.put(name, v));
    return map;
  }
}
