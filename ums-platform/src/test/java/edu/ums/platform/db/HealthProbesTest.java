package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.observe.HealthStatus;
import edu.ums.platform.observe.ProbeResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HealthProbesTest {

  @Test
  void databaseProbeIsUpWhenSelectOneWorksAndUsesTwoSecondTimeout() throws SQLException {
    DataSource ds = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(ds.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);

    ProbeResult result = new DatabaseReachableProbe(ds).check();

    assertThat(result.name()).isEqualTo("database");
    assertThat(result.status()).isEqualTo(HealthStatus.UP);
    Mockito.verify(statement).setQueryTimeout(2);
    Mockito.verify(statement).execute("SELECT 1");
  }

  @Test
  void databaseProbeIsDownWithSqlStateWhenUnreachable() throws SQLException {
    DataSource ds = mock(DataSource.class);
    when(ds.getConnection()).thenThrow(new SQLException("refused", "08001"));

    ProbeResult result = new DatabaseReachableProbe(ds).check();

    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.detail()).isEqualTo("unreachable (sqlState=08001)");
  }

  @Test
  void migrationsProbeReflectsPendingAndFutureMigrations() {
    MigrationRunner runner = mock(MigrationRunner.class);
    when(runner.status())
        .thenReturn(
            new MigrationStatus(0, 0), new MigrationStatus(2, 0), new MigrationStatus(0, 1));

    MigrationsCurrentProbe probe = new MigrationsCurrentProbe(runner);

    ProbeResult current = probe.check();
    assertThat(current.status()).isEqualTo(HealthStatus.UP);
    assertThat(current.detail()).isEqualTo("schema current");
    ProbeResult behind = probe.check();
    assertThat(behind.status()).isEqualTo(HealthStatus.DEGRADED);
    assertThat(behind.detail()).isEqualTo("2 migration(s) pending");
    ProbeResult ahead = probe.check();
    assertThat(ahead.status()).isEqualTo(HealthStatus.UP);
    assertThat(ahead.detail()).isEqualTo("schema ahead of application (rollback mode)");
    assertThat(probe.name()).isEqualTo("migrations");
  }

  @Test
  void migrationsProbeIsDownWhenStateCannotBeRead() {
    MigrationRunner runner = mock(MigrationRunner.class);
    when(runner.status())
        .thenThrow(
            new InfrastructureException(ErrorCode.INTERNAL_ERROR, new IllegalStateException()));

    ProbeResult result = new MigrationsCurrentProbe(runner).check();

    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.detail()).contains("INTERNAL_ERROR");
  }
}
