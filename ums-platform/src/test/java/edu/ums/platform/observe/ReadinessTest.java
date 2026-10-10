package edu.ums.platform.observe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.platform.error.ApiErrorTranslator;
import edu.ums.platform.invoke.ErrorMappingInterceptor;
import edu.ums.platform.invoke.FacadeProxyFactory;
import edu.ums.platform.invoke.Interceptor;
import edu.ums.platform.invoke.InterceptorSlot;
import edu.ums.platform.invoke.Invocation;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Proves the readiness gate with a test-only facade: until ready, every call fails with
 * DB_UNAVAILABLE. P1 moves the check into the real SESSION interceptor.
 */
public class ReadinessTest {

  /** Test-only facade (public: the proxy factory invokes it from another package). */
  public interface PingApi {
    String ping();
  }

  @Test
  void facadeCallsFailWithDbUnavailableUntilReady() {
    Readiness readiness = new Readiness();
    Interceptor gate =
        new Interceptor() {
          @Override
          public Object intercept(Invocation invocation) throws Throwable {
            readiness.requireReady();
            return invocation.proceed();
          }

          @Override
          public InterceptorSlot slot() {
            return InterceptorSlot.SESSION;
          }
        };
    PingApi api =
        new FacadeProxyFactory()
            .create(
                PingApi.class,
                () -> "pong",
                List.of(gate, new ErrorMappingInterceptor(new ApiErrorTranslator(new Random(3)))));

    assertDbUnavailable(api);

    readiness.markReady();
    assertThat(readiness.isReady()).isTrue();
    assertThat(api.ping()).isEqualTo("pong");

    readiness.markNotReady();
    assertDbUnavailable(api);
  }

  private static void assertDbUnavailable(PingApi api) {
    assertThatThrownBy(api::ping)
        .isInstanceOfSatisfying(
            UmsApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DB_UNAVAILABLE));
  }
}
