package edu.ums.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorAndPageTest {

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void blankCursorIsRejected(String token) {
    assertValidationFailed(() -> new Cursor(token));
  }

  @Test
  void nullCursorIsRejected() {
    assertValidationFailed(() -> new Cursor(null));
  }

  @Test
  void cursorLengthBoundary() {
    assertThat(new Cursor("a".repeat(Cursor.MAX_LENGTH)).token()).hasSize(512);
    assertValidationFailed(() -> new Cursor("a".repeat(Cursor.MAX_LENGTH + 1)));
  }

  @Test
  void cursorToStringHidesToken() {
    assertThat(new Cursor("secret-keyset").toString()).doesNotContain("secret");
  }

  @Test
  void pageCopiesItemsAndIsUnmodifiable() {
    List<String> source = new ArrayList<>(List.of("a", "b"));
    Page<String> page = new Page<>(source, Optional.of(new Cursor("t")));
    source.add("c");

    assertThat(page.items()).containsExactly("a", "b");
    assertThatThrownBy(() -> page.items().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void pageRejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new Page<>(null, Optional.empty()));
    assertThatNullPointerException().isThrownBy(() -> new Page<>(List.of(), null));
  }

  @Test
  void lastPageHasNoNext() {
    assertThat(Page.last(List.of(1)).next()).isEmpty();
  }

  private static void assertValidationFailed(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            UmsApiException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }
}
