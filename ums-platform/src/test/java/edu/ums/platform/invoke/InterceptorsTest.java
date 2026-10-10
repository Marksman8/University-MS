package edu.ums.platform.invoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.platform.db.RetryPolicy;
import edu.ums.platform.db.TransactionRunner;
import edu.ums.platform.error.ApiErrorTranslator;
import edu.ums.platform.error.ConflictException;
import edu.ums.platform.error.ConstraintErrorMap;
import edu.ums.platform.error.NotFoundException;
import edu.ums.platform.error.SqlErrorMapper;
import edu.ums.platform.observe.MetricsRegistry;
import edu.ums.platform.observe.Tracer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class InterceptorsTest {

  private final FacadeProxyFactory factory = new FacadeProxyFactory();
  private final ApiErrorTranslator translator = new ApiErrorTranslator(new Random(1));
  private final Tracer tracer = new Tracer();
  private final MetricsRegistry metrics = new MetricsRegistry();
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final TransactionRunner runner =
      new TransactionRunner(
          dataSource,
          new SqlErrorMapper(ConstraintErrorMap.standard()),
          new RetryPolicy(new Random(2), millis -> {}));

  @BeforeEach
  void connect() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
  }

  // --- ErrorMappingInterceptor ---

  @Test
  void domainFailureFromTargetBecomesApiException() {
    DemoApi api = chain(failing(new NotFoundException(Map.of("entity", "term"))), errorMapping());
    assertThatThrownBy(() -> api.read("x"))
        .isInstanceOfSatisfying(
            UmsApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
              assertThat(e.args()).containsEntry("entity", "term");
            });
  }

  @Test
  void unknownFailureBecomesInternalErrorWithIncident() {
    DemoApi api = chain(failing(new IllegalStateException("secret detail")), errorMapping());
    assertThatThrownBy(() -> api.read("x"))
        .isInstanceOfSatisfying(
            UmsApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
              assertThat(e.incidentId()).isPresent();
              assertThat(e.getMessage()).doesNotContain("secret");
            });
  }

  @Test
  void virtualMachineErrorsAreNotTranslated() {
    DemoApi api = chain(failing(new StackOverflowError()), errorMapping());
    assertThatThrownBy(() -> api.read("x")).isInstanceOf(StackOverflowError.class);
  }

  // --- TracingInterceptor ---

  @Test
  void tracingSetsMdcDuringCallAndClearsItAfter() {
    AtomicReference<String> seen = new AtomicReference<>();
    DemoApi api = chain(calling(() -> seen.set(MDC.get(Tracer.MDC_TRACE_ID))), tracing());

    api.read("x");

    assertThat(seen.get()).isNotBlank();
    assertThat(MDC.get(Tracer.MDC_TRACE_ID)).isNull();
    assertThat(tracer.current()).isEmpty();
    assertThat(metrics.snapshot().get("DemoApi.read"))
        .isEqualTo(
            new MetricsRegistry.MethodStats(
                1, metrics.snapshot().get("DemoApi.read").totalNanos(), 0));
  }

  @Test
  void nestedCallJoinsTheOuterTrace() {
    AtomicReference<String> inner = new AtomicReference<>();
    DemoApi innerApi = chain(calling(() -> inner.set(MDC.get(Tracer.MDC_TRACE_ID))), tracing());
    AtomicReference<String> outer = new AtomicReference<>();
    DemoApi outerApi =
        chain(
            calling(
                () -> {
                  outer.set(MDC.get(Tracer.MDC_TRACE_ID));
                  innerApi.read("nested");
                }),
            tracing());

    outerApi.read("x");

    assertThat(inner.get()).isEqualTo(outer.get()).isNotNull();
    assertThat(MDC.get(Tracer.MDC_TRACE_ID)).isNull();
  }

  @Test
  void tracingCountsFailuresAndStillClearsTheThread() {
    DemoApi api = chain(failing(new NotFoundException(Map.of())), tracing());
    assertThatThrownBy(() -> api.write("x")).isInstanceOf(NotFoundException.class);
    assertThat(metrics.snapshot().get("DemoApi.write").errors()).isEqualTo(1);
    assertThat(tracer.current()).isEmpty();
    assertThat(MDC.get(Tracer.MDC_TRACE_ID)).isNull();
  }

  // --- TransactionInterceptor ---

  @Test
  void writesRunReadWriteAndReadsRunReadOnly() throws SQLException {
    DemoApi api = chain(calling(() -> {}), transaction());

    api.write("x");
    verify(connection).setReadOnly(false);
    api.read("x");
    verify(connection).setReadOnly(true);
    verify(connection, times(2)).commit();
  }

  @Test
  void failureRollsBackAndCommitsNothing() throws SQLException {
    DemoApi api = chain(failing(new NotFoundException(Map.of())), errorMapping(), transaction());

    assertThatThrownBy(() -> api.write("x")).isInstanceOf(UmsApiException.class);
    verify(connection).rollback();
    verify(connection, never()).commit();
  }

  @Test
  void conflictIsRetriedForThreeAttemptsInTotalThenMapped() {
    AtomicInteger attempts = new AtomicInteger();
    DemoApi api =
        chain(
            calling(
                () -> {
                  attempts.incrementAndGet();
                  throw new ConflictException(null);
                }),
            errorMapping(),
            tracing(),
            transaction());

    assertThatThrownBy(() -> api.write("x"))
        .isInstanceOfSatisfying(
            UmsApiException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT_TRY_AGAIN));
    assertThat(attempts).hasValue(RetryPolicy.MAX_ATTEMPTS);
  }

  @Test
  void joinedInnerFacadeCallNeverRetriesOnItsOwn() throws SQLException {
    AtomicInteger innerCalls = new AtomicInteger();
    AtomicInteger outerCalls = new AtomicInteger();
    DemoApi inner =
        chain(
            calling(
                () -> {
                  innerCalls.incrementAndGet();
                  throw new ConflictException(null);
                }),
            transaction());
    DemoApi outer =
        chain(
            calling(
                () -> {
                  outerCalls.incrementAndGet();
                  inner.write("nested");
                }),
            errorMapping(),
            transaction());

    assertThatThrownBy(() -> outer.write("x")).isInstanceOf(UmsApiException.class);

    // Only the outermost transaction retries: exactly one inner call per outer attempt.
    assertThat(outerCalls).hasValue(RetryPolicy.MAX_ATTEMPTS);
    assertThat(innerCalls).hasValue(RetryPolicy.MAX_ATTEMPTS);
    verify(dataSource, times(RetryPolicy.MAX_ATTEMPTS)).getConnection();
  }

  // --- helpers ---

  private DemoApi chain(DemoApi target, Interceptor... interceptors) {
    return factory.create(DemoApi.class, target, List.of(interceptors));
  }

  private Interceptor errorMapping() {
    return new ErrorMappingInterceptor(translator);
  }

  private Interceptor tracing() {
    return new TracingInterceptor(tracer, metrics);
  }

  private Interceptor transaction() {
    return new TransactionInterceptor(runner);
  }

  private static DemoApi failing(Throwable failure) {
    return calling(
        () -> {
          if (failure instanceof RuntimeException r) {
            throw r;
          }
          throw (Error) failure;
        });
  }

  private static DemoApi calling(Runnable body) {
    return new DemoApi() {
      @Override
      public String read(String input) {
        body.run();
        return input;
      }

      @Override
      public String write(String input) {
        body.run();
        return input;
      }
    };
  }
}
