package edu.ums.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

  private static final Map<String, String> BASE =
      Map.ofEntries(
          Map.entry("ums.db.url", "jdbc:postgresql://localhost:5432/ums"),
          Map.entry("ums.db.user", "ums_app"),
          Map.entry("ums.db.console.user", "ums_dev_console"),
          Map.entry("ums.db.listener.user", "ums_listener"),
          Map.entry("ums.db.owner.user", "ums_owner"),
          Map.entry("ums.db.backup.user", "ums_backup"),
          Map.entry("ums.db.statement-timeout", "PT10S"),
          Map.entry("ums.db.lock-timeout", "PT5S"),
          Map.entry("ums.db.console.statement-timeout", "PT15S"),
          Map.entry("ums.pool.app.max-size", "10"),
          Map.entry("ums.pool.console.max-size", "3"),
          Map.entry("ums.pool.connection-timeout", "PT5S"),
          Map.entry("ums.policy.credit-limit", "26"),
          Map.entry("ums.policy.attendance-threshold-percent", "75"),
          Map.entry("ums.policy.attendance-edit-window", "PT48H"),
          Map.entry("ums.policy.lockout.max-failures", "5"),
          Map.entry("ums.policy.lockout.duration", "PT15M"),
          Map.entry("ums.policy.session-idle-timeout", "PT30M"),
          Map.entry("ums.backup.dir", "backups"),
          Map.entry("ums.backup.second-dir", "backups/weekly"),
          Map.entry("ums.log.dir", "logs"));

  private static final Map<String, String> SECRETS =
      Map.of(
          "UMS_DB_PASSWORD", "app-pw-123",
          "UMS_CONSOLE_DB_PASSWORD", "console-pw-123",
          "UMS_LISTENER_DB_PASSWORD", "listener-pw-123",
          "UMS_OWNER_DB_PASSWORD", "owner-pw-123",
          "UMS_BACKUP_DB_PASSWORD", "backup-pw-123");

  @Test
  void loadsCompleteConfigurationWithDefaultsAndLocalEnvironment() {
    AppConfig config = loader(Map.of(), SECRETS).load();

    assertThat(config.environment()).isEqualTo(Environment.LOCAL);
    assertThat(config.database().app().user()).isEqualTo("ums_app");
    assertThat(config.database().owner().password().reveal())
        .containsExactly("owner-pw-123".toCharArray());
    assertThat(config.pools().appMaxSize()).isEqualTo(10);
    assertThat(config.policy().creditLimit()).isEqualTo(26);
    assertThat(config.policy().attendanceEditWindow()).isEqualTo(Duration.ofHours(48));
    assertThat(config.policy().sessionIdleTimeout()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void listsAllMissingVariablesTogether() {
    Map<String, String> base = new HashMap<>(BASE);
    base.remove("ums.db.url");
    ConfigLoader loader = new ConfigLoader(name -> source(name, base, Map.of()), Map.of());

    assertThatThrownBy(loader::load)
        .isInstanceOfSatisfying(
            ConfigException.class,
            e ->
                assertThat(e.problems())
                    .containsExactlyInAnyOrder(
                        "ums.db.url (UMS_DB_URL): required",
                        "UMS_DB_PASSWORD: required (environment variable)",
                        "UMS_CONSOLE_DB_PASSWORD: required (environment variable)",
                        "UMS_LISTENER_DB_PASSWORD: required (environment variable)",
                        "UMS_OWNER_DB_PASSWORD: required (environment variable)",
                        "UMS_BACKUP_DB_PASSWORD: required (environment variable)"));
  }

  @Test
  void listsInvalidValuesAndInvalidEnvironmentTogether() {
    Map<String, String> env = new HashMap<>(SECRETS);
    env.put("UMS_ENV", "moon");
    env.put("UMS_POOL_APP_MAX_SIZE", "lots");
    env.put("UMS_POLICY_LOCKOUT_DURATION", "-PT1M");
    env.put("UMS_DB_URL", "jdbc:mysql://localhost/ums");

    assertThatThrownBy(() -> loader(Map.of(), env).load())
        .isInstanceOfSatisfying(
            ConfigException.class,
            e -> {
              assertThat(e.problems()).hasSize(4);
              assertThat(e.getMessage())
                  .contains(
                      "UMS_ENV: must be one of",
                      "ums.pool.app.max-size",
                      "ums.policy.lockout.duration")
                  .contains("must start with jdbc:postgresql:");
            });
  }

  @Test
  void secretValuesNeverAppearInErrors() {
    Map<String, String> env = new HashMap<>(SECRETS);
    env.put("UMS_POOL_CONSOLE_MAX_SIZE", "999");

    assertThatThrownBy(() -> loader(Map.of(), env).load())
        .isInstanceOfSatisfying(
            ConfigException.class,
            e -> SECRETS.values().forEach(pw -> assertThat(e.getMessage()).doesNotContain(pw)));
  }

  @Test
  void profileFileOverridesBaseAndEnvironmentOverridesBoth() {
    Map<String, String> env = new HashMap<>(SECRETS);
    env.put("UMS_ENV", "test");
    env.put("UMS_POLICY_CREDIT_LIMIT", "30");
    Map<String, String> profile =
        Map.of("ums.policy.credit-limit", "20", "ums.pool.app.max-size", "4");

    AppConfig config = loader(profile, env).load();

    assertThat(config.environment()).isEqualTo(Environment.TEST);
    assertThat(config.pools().appMaxSize()).isEqualTo(4);
    assertThat(config.policy().creditLimit()).isEqualTo(30);
  }

  @Test
  void secretsInPropertiesFilesAreIgnored() {
    Map<String, String> base = new HashMap<>(BASE);
    base.put("UMS_DB_PASSWORD", "from-file");
    Map<String, String> env = new HashMap<>(SECRETS);
    env.remove("UMS_DB_PASSWORD");
    ConfigLoader loader = new ConfigLoader(name -> source(name, base, Map.of()), env);

    assertThatThrownBy(loader::load)
        .isInstanceOfSatisfying(
            ConfigException.class,
            e ->
                assertThat(e.problems())
                    .containsExactly("UMS_DB_PASSWORD: required (environment variable)"));
  }

  private static ConfigLoader loader(Map<String, String> profile, Map<String, String> env) {
    return new ConfigLoader(name -> source(name, BASE, profile), env);
  }

  private static Optional<Map<String, String>> source(
      String name, Map<String, String> base, Map<String, String> profile) {
    if (name.equals(ConfigLoader.BASE_RESOURCE)) {
      return Optional.of(base);
    }
    return name.equals("application-test.properties") ? Optional.of(profile) : Optional.empty();
  }
}
