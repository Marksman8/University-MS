package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.config.AppConfig;
import edu.ums.platform.config.Environment;
import edu.ums.platform.config.Secret;
import edu.ums.platform.error.InfrastructureException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MigrationRunnerTest {

  @ParameterizedTest
  @EnumSource(value = Environment.class, names = "TEST", mode = EnumSource.Mode.EXCLUDE)
  void cleanIsRefusedOutsideTest(Environment environment) {
    MigrationRunner runner = new MigrationRunner(mock(DataSource.class), environment);
    assertThatThrownBy(runner::clean)
        .isInstanceOfSatisfying(
            InfrastructureException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR))
        .hasRootCauseMessage("clean is disabled in " + environment);
  }

  @Test
  void unreachableDatabaseFailsMigrateAsInternalError() throws SQLException {
    DataSource broken = mock(DataSource.class);
    when(broken.getConnection()).thenThrow(new SQLException("refused", "08001"));
    MigrationRunner runner = new MigrationRunner(broken, Environment.TEST);

    assertThatThrownBy(runner::migrate)
        .isInstanceOfSatisfying(
            InfrastructureException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    assertThatThrownBy(runner::pendingCount).isInstanceOf(InfrastructureException.class);
    assertThatThrownBy(runner::clean).isInstanceOf(InfrastructureException.class);
  }

  @Test
  void forOwnerBuildsWithoutConnectingAndKeepsTheSecretUsable() {
    AppConfig.DbAccount account = new AppConfig.DbAccount("ums_owner", Secret.of("owner-password"));
    AppConfig config =
        new AppConfig(
            Environment.LOCAL,
            new AppConfig.DatabaseConfig(
                "jdbc:postgresql://localhost:1/none", account, account, account, account, account),
            new AppConfig.PoolConfig(
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)),
            new AppConfig.PolicyDefaults(
                26, 75, Duration.ofHours(48), 5, Duration.ofMinutes(15), Duration.ofMinutes(30)),
            new AppConfig.PathsConfig(Path.of("b"), Path.of("b2"), Path.of("l")));

    assertThat(MigrationRunner.forOwner(config)).isNotNull();
    assertThat(account.password().reveal()).containsExactly("owner-password".toCharArray());
  }
}
