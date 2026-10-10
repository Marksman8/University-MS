package edu.ums.platform.invoke;

import edu.ums.platform.error.ApiErrorTranslator;
import java.util.Objects;

/**
 * Outermost link: every failure leaving a facade becomes a {@link
 * edu.ums.api.error.UmsApiException}. JVM-fatal errors ({@link VirtualMachineError}) propagate
 * unchanged.
 */
// Complexity: time O(a) per failed call (argument filtering), O(1) otherwise; space O(a)
public final class ErrorMappingInterceptor implements Interceptor {

  private final ApiErrorTranslator translator;

  /** Creates the interceptor. */
  public ErrorMappingInterceptor(ApiErrorTranslator translator) {
    this.translator = Objects.requireNonNull(translator, "translator");
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    try {
      return invocation.proceed();
    } catch (Throwable failure) { // CHECKSTYLE:ALLOW-CATCH facade boundary: map every failure
      if (failure instanceof VirtualMachineError fatal) {
        throw fatal;
      }
      throw translator.translate(failure);
    }
  }

  @Override
  public InterceptorSlot slot() {
    return InterceptorSlot.ERROR_MAPPING;
  }
}
