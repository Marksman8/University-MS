package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.dto.Cursor;
import edu.ums.api.error.ErrorCode;
import edu.ums.platform.db.KeysetQuery.KeyColumn;
import edu.ums.platform.error.InfrastructureException;
import edu.ums.platform.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorCodecTest {

  private static final List<KeyColumn> KEYS =
      List.of(
          new KeyColumn("occurred_at", KeyType.TIMESTAMP),
          new KeyColumn("username", KeyType.STRING),
          new KeyColumn("id", KeyType.LONG));

  private final CursorCodec codec = new CursorCodec();

  @Property
  void roundTripsAnyKey(
      @ForAll @LongRange(min = -4_000_000_000L, max = 4_000_000_000L) long epochSecond,
      @ForAll @StringLength(max = 64) String text,
      @ForAll long id) {
    Instant at = Instant.ofEpochSecond(epochSecond, 123_456_000);
    Cursor cursor = codec.encode(List.of(at, text, id), KEYS);

    assertThat(codec.decode(cursor, KEYS)).containsExactly(at, text, id);
    assertThat(cursor.token()).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void offsetDateTimeEncodesAsInstant() {
    OffsetDateTime odt = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(5));
    Cursor cursor = codec.encode(List.of(odt, "x", 1L), KEYS);
    assertThat(codec.decode(cursor, KEYS).get(0)).isEqualTo(odt.toInstant());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not base64 !", "e30", "WyJhIl0", "WyIyMDI2IiwieCIsIjEiXQ", "bnVsbA"})
  void tamperedCursorIsValidationFailed(String token) {
    assertThatThrownBy(() -> codec.decode(new Cursor(token), KEYS))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void wrongTypeInsideValidShapeIsValidationFailed() {
    String json = "[\"2026-01-01T00:00:00Z\",\"x\",\"not-a-number\"]";
    String token =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> codec.decode(new Cursor(token), KEYS))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void keyThatDoesNotFitIsInternalError() {
    List<KeyColumn> keys = List.of(new KeyColumn("name", KeyType.STRING));
    assertThatThrownBy(() -> codec.encode(List.of("x".repeat(600)), keys))
        .isInstanceOfSatisfying(
            InfrastructureException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
  }

  @Test
  void valueCountMustMatchKeys() {
    assertThatThrownBy(() -> codec.encode(List.of(1L), KEYS))
        .isInstanceOf(InfrastructureException.class);
  }
}
