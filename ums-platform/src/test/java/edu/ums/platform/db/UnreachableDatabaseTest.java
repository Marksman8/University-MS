package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.Environment;
import edu.ums.platform.config.Secret;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.error.UmsException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Pools and the listener fail fast with DB_UNAVAILABLE when nothing listens (no database needed).
 */
class UnreachableDatabaseTest {

  private static final String NOWHERE = "jdbc:postgresql://127.0.0.1:1/ums";

  private final SqlErrorMapper errors = new SqlErrorMapper(ConstraintErrorMap.standard());

  @Test
  void poolsFailFastWithDbUnavailable() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () ->
            assertThatThrownBy(() -> DataSources.create(config(), errors))
                .isInstanceOfSatisfying(
                    UmsException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.DB_UNAVAILABLE)));
  }

  @Test
  void listenerConnectionFailsFastWithDbUnavailable() {
    ListenerConnectionFactory factory = new ListenerConnectionFactory(config(), errors);
    assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () ->
            assertThatThrownBy(factory::open)
                .isInstanceOfSatisfying(
                    UmsException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.DB_UNAVAILABLE)));
  }

  private static AppConfig config() {
    AppConfig.DbAccount account = new AppConfig.DbAccount("nobody", Secret.of("nothing"));
    return new AppConfig(
        Environment.TEST,
        new AppConfig.DatabaseConfig(NOWHERE, account, account, account, account, account),
        new AppConfig.PoolConfig(
            2,
            1,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)),
        new AppConfig.PolicyDefaults(
            26, 75, Duration.ofHours(48), 5, Duration.ofMinutes(15), Duration.ofMinutes(30)),
        new AppConfig.PathsConfig(Path.of("b"), Path.of("b2"), Path.of("l")));
  }
}
