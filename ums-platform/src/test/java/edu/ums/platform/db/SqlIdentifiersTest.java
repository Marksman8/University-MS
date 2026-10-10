package edu.ums.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlIdentifiersTest {

  @ParameterizedTest
  @ValueSource(strings = {"id", "_x", "activity_log", "term2", "a"})
  void acceptsPlainIdentifiers(String name) {
    assertThat(SqlIdentifiers.isPlain(name)).isTrue();
    assertThat(SqlIdentifiers.isColumn(name)).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"2x", "Id", "a-b", "a b", "id;drop", "x\"", "a.b"})
  void rejectsNonPlainIdentifiers(String name) {
    assertThat(SqlIdentifiers.isPlain(name)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a.id", "al.occurred_at"})
  void acceptsQualifiedColumns(String name) {
    assertThat(SqlIdentifiers.isColumn(name)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a.b.c", ".id", "a.", "a..b", "A.id"})
  void rejectsMalformedQualifiedColumns(String name) {
    assertThat(SqlIdentifiers.isColumn(name)).isFalse();
  }

  @org.junit.jupiter.api.Test
  void enforcesPostgresLengthLimit() {
    assertThat(SqlIdentifiers.isPlain("a".repeat(63))).isTrue();
    assertThat(SqlIdentifiers.isPlain("a".repeat(64))).isFalse();
    assertThat(SqlIdentifiers.isColumn(null)).isFalse();
  }
}
