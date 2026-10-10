package edu.ums.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeTest {

  private static final Pattern KEY_FORMAT =
      Pattern.compile("error\\.(auth|policy|domain|offer|reg|att|marks|infra)\\.[a-z]+(_[a-z]+)*");

  /** Exactly the codes listed in master prompt §10, in order. */
  private static final String MASTER_CODES =
      "BAD_CREDENTIALS ACCOUNT_LOCKED SESSION_EXPIRED ACCESS_DENIED PASSWORD_POLICY LAST_ADMIN"
          + " USERNAME_TAKEN FEATURE_DISABLED RATE_LIMITED CONFIRMATION_REQUIRED VALIDATION_FAILED"
          + " NOT_FOUND STALE_DATA INVALID_TRANSITION GUARD_FAILED SLOT_CLASH INVALID_SCHEME"
          + " WINDOW_CLOSED ALREADY_ENROLLED PREREQ_NOT_MET CREDIT_LIMIT TIMETABLE_CLASH SECTION_FULL"
          + " EDIT_WINDOW_CLOSED INVALID_SESSION_DATE MARK_OUT_OF_RANGE NOT_EDITABLE MARKS_INCOMPLETE"
          + " DB_UNAVAILABLE BUSY_TRY_AGAIN CONFLICT_TRY_AGAIN TIMEOUT CANCELLED INTERNAL_ERROR";

  private static Properties messages;

  @BeforeAll
  static void loadBundle() throws IOException {
    messages = new Properties();
    try (InputStream in = ErrorCodeTest.class.getResourceAsStream("/i18n/messages_en.properties")) {
      assertThat(in).as("messages_en.properties on classpath").isNotNull();
      messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
  }

  @Test
  void codesMatchMasterPromptExactly() {
    assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
        .containsExactly(MASTER_CODES.split(" "));
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyCodeHasNonBlankMessage(ErrorCode code) {
    assertThat(messages.getProperty(code.messageKey())).isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void keyIsWellFormedAndUsesTheCodesArea(ErrorCode code) {
    assertThat(code.messageKey()).matches(KEY_FORMAT);
    assertThat(code.messageKey()).startsWith("error." + code.area().key() + ".");
  }

  @Test
  void keysAreUnique() {
    Set<String> keys =
        Arrays.stream(ErrorCode.values()).map(ErrorCode::messageKey).collect(Collectors.toSet());
    assertThat(keys).hasSize(ErrorCode.values().length);
  }

  @Test
  void bundleHasNoOrphanKeys() {
    Set<String> expected =
        Arrays.stream(ErrorCode.values()).map(ErrorCode::messageKey).collect(Collectors.toSet());
    assertThat(messages.stringPropertyNames()).isEqualTo(expected);
  }
}
