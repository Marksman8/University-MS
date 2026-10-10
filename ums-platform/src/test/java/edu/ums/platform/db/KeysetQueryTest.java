package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import edu.ums.platform.db.KeysetQuery.Direction;
import edu.ums.platform.db.KeysetQuery.KeyColumn;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class KeysetQueryTest {

  private static final List<KeyColumn> KEYS =
      List.of(new KeyColumn("occurred_at", KeyType.TIMESTAMP), new KeyColumn("id", KeyType.LONG));

  @Test
  void descendingWithFilterAndSeek() {
    assertThat(query("role = ?", Direction.DESC).sql(true))
        .isEqualTo(
            "SELECT id, occurred_at FROM activity_log WHERE (role = ?) AND (occurred_at, id) <"
                + " (?, ?) ORDER BY occurred_at DESC, id DESC LIMIT ?");
  }

  @Test
  void firstPageHasNoSeek() {
    assertThat(query("role = ?", Direction.DESC).sql(false))
        .isEqualTo(
            "SELECT id, occurred_at FROM activity_log WHERE role = ? ORDER BY occurred_at DESC,"
                + " id DESC LIMIT ?");
  }

  @Test
  void ascendingWithoutFilter() {
    assertThat(query("", Direction.ASC).sql(true))
        .isEqualTo(
            "SELECT id, occurred_at FROM activity_log WHERE (occurred_at, id) > (?, ?) ORDER BY"
                + " occurred_at ASC, id ASC LIMIT ?");
    assertThat(query("", Direction.ASC).sql(false))
        .isEqualTo(
            "SELECT id, occurred_at FROM activity_log ORDER BY occurred_at ASC, id ASC LIMIT ?");
  }

  @Test
  void rejectsUnsafeOrMissingKeyColumns() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new KeysetQuery<>(
                    "SELECT 1",
                    "",
                    List.of(),
                    List.of(new KeyColumn("id; DROP", KeyType.LONG)),
                    Direction.ASC,
                    rs -> 1,
                    r -> List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new KeysetQuery<>(
                    "SELECT 1", "", List.of(), List.of(), Direction.ASC, rs -> 1, r -> List.of()));
  }

  @Test
  void sessionSettingsInitSqlAndDriverProperties() {
    assertThat(SessionSettings.initSql(Duration.ofSeconds(10), Duration.ofSeconds(5)))
        .isEqualTo(
            "SET search_path = ums; SET statement_timeout = '10000ms'; SET lock_timeout = '5000ms'");
    Properties properties = SessionSettings.driverProperties("ums-app");
    assertThat(properties.getProperty("logServerErrorDetail")).isEqualTo("false");
    assertThat(properties.getProperty("ApplicationName")).isEqualTo("ums-app");
  }

  private static KeysetQuery<Long> query(String where, Direction direction) {
    return new KeysetQuery<>(
        "SELECT id, occurred_at FROM activity_log",
        where,
        List.of(),
        KEYS,
        direction,
        rs -> rs.getLong(1),
        id -> List.of());
  }
}
