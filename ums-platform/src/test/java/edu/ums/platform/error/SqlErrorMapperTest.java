package edu.ums.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import edu.ums.api.error.ErrorCode;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

class SqlErrorMapperTest {

  private final SqlErrorMapper mapper = new SqlErrorMapper(ConstraintErrorMap.standard());

  static Stream<Arguments> stateTable() {
    return Stream.of(
        Arguments.of("08001", ErrorCode.DB_UNAVAILABLE, DataUnavailableException.class),
        Arguments.of("08006", ErrorCode.DB_UNAVAILABLE, DataUnavailableException.class),
        Arguments.of("40001", ErrorCode.CONFLICT_TRY_AGAIN, ConflictException.class),
        Arguments.of("40P01", ErrorCode.CONFLICT_TRY_AGAIN, ConflictException.class),
        Arguments.of("23503", ErrorCode.NOT_FOUND, NotFoundException.class),
        Arguments.of("55P03", ErrorCode.BUSY_TRY_AGAIN, BusyException.class),
        Arguments.of("XX000", ErrorCode.INTERNAL_ERROR, InfrastructureException.class),
        Arguments.of("42P01", ErrorCode.INTERNAL_ERROR, InfrastructureException.class));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("stateTable")
  void mapsSqlState(String state, ErrorCode expected, Class<? extends UmsException> type) {
    SQLException e = new SQLException("driver message", state);
    UmsException mapped = mapper.map(e, false);
    assertThat(mapped).isInstanceOf(type).hasCause(e);
    assertThat(mapped.code()).isEqualTo(expected);
  }

  static Stream<Arguments> constraintTable() {
    return Stream.of(
        Arguments.of("23505", "uq_enrolment_live", ErrorCode.ALREADY_ENROLLED),
        Arguments.of("23505", "uq_app_user_username", ErrorCode.USERNAME_TAKEN),
        Arguments.of("23P01", "ex_room_slot", ErrorCode.SLOT_CLASH),
        Arguments.of("23P01", "ex_faculty_slot", ErrorCode.SLOT_CLASH),
        Arguments.of("23514", "ck_section_capacity", ErrorCode.SECTION_FULL),
        Arguments.of("23514", "ck_mark_nonneg", ErrorCode.MARK_OUT_OF_RANGE),
        Arguments.of("23505", "uq_one_active_term", ErrorCode.GUARD_FAILED),
        Arguments.of("23505", "uq_result_current", ErrorCode.STALE_DATA),
        Arguments.of("23505", "some_unregistered_key", ErrorCode.INTERNAL_ERROR),
        Arguments.of("23514", "some_unregistered_check", ErrorCode.INTERNAL_ERROR));
  }

  @ParameterizedTest(name = "{0} {1} -> {2}")
  @MethodSource("constraintTable")
  void mapsConstraintViolations(String state, String constraint, ErrorCode expected) {
    PSQLException e = psql(state, constraint);
    UmsException mapped = mapper.map(e, false);
    assertThat(mapped.code()).isEqualTo(expected);
    assertThat(mapped).hasCause(e);
    assertThat(mapped.details()).isEmpty();
  }

  @Test
  void guardAndStaleConstraintsUseTheirDedicatedTypes() {
    assertThat(mapper.map(psql("23505", "uq_one_active_term"), false))
        .isInstanceOf(GuardFailedException.class);
    assertThat(mapper.map(psql("23505", "uq_result_current"), false))
        .isInstanceOf(StaleDataException.class);
    assertThat(mapper.map(psql("23505", "uq_enrolment_live"), false))
        .isInstanceOf(RuleViolationException.class);
  }

  @Test
  void statementCancelMapsToTimeoutOrCancelled() {
    SQLException e = new SQLException("canceling statement", "57014");
    assertThat(mapper.map(e, false).code()).isEqualTo(ErrorCode.TIMEOUT);
    assertThat(mapper.map(e, true).code()).isEqualTo(ErrorCode.CANCELLED);
  }

  @Test
  void hikariPoolTimeoutIsBusy() {
    SQLTransientConnectionException e =
        new SQLTransientConnectionException(
            "ums-app - Connection is not available, request timed out after 5000ms.");
    assertThat(mapper.map(e, false)).isInstanceOf(BusyException.class);
  }

  @Test
  void hikariTimeoutCausedByUnreachableDatabaseIsUnavailable() {
    SQLTransientConnectionException e =
        new SQLTransientConnectionException("ums-app - Connection is not available", "08001");
    assertThat(mapper.map(e, false)).isInstanceOf(DataUnavailableException.class);
  }

  @Test
  void stateIsFoundAlongNextExceptionChain() {
    SQLException batch = new SQLException("Batch entry 0 failed");
    batch.setNextException(new SQLException("could not serialize", "40001"));
    assertThat(mapper.map(batch, false).code()).isEqualTo(ErrorCode.CONFLICT_TRY_AGAIN);
  }

  @Test
  void constraintIsFoundOnWrappedPsqlException() {
    SQLException wrapper = new SQLException("wrapped", "23505", psql("23505", "uq_enrolment_live"));
    assertThat(mapper.map(wrapper, false).code()).isEqualTo(ErrorCode.ALREADY_ENROLLED);
  }

  @Test
  void missingStateIsInternalError() {
    assertThat(mapper.map(new SQLException("no state"), false).code())
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void cyclicCauseChainTerminates() {
    SQLException a = new SQLException("a");
    SQLException b = new SQLException("b", (String) null, a);
    a.initCause(b);
    assertThat(mapper.map(a, false).code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  static PSQLException psql(String state, String constraint) {
    return new PSQLException(
        new ServerErrorMessage(
            "SERROR\0C"
                + state
                + "\0Mduplicate key value violates unique constraint\0n"
                + constraint
                + "\0"));
  }
}
