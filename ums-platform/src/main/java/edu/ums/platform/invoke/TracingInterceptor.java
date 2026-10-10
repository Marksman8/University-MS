package edu.ums.platform.invoke;

import edu.ums.platform.observe.MetricsRegistry;
import edu.ums.platform.observe.Tracer;
import java.util.Objects;

/**
 * Opens (or joins) a trace for the call, putting {@code traceId} in the MDC, and records count,
 * duration and failures per facade method in the {@link MetricsRegistry}.
 */
// Complexity: time O(1) per call; space O(1) per call
public final class TracingInterceptor implements Interceptor {

  private final Tracer tracer;
  private final MetricsRegistry metrics;

  /** Creates the interceptor. */
  public TracingInterceptor(Tracer tracer, MetricsRegistry metrics) {
    this.tracer = Objects.requireNonNull(tracer, "tracer");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    String key = invocation.declaringType().getSimpleName() + "." + invocation.methodName();
    Tracer.Scope scope = tracer.open();
    long start = System.nanoTime();
    boolean succeeded = false;
    try {
      Object result = invocation.proceed();
      succeeded = true;
      return result;
    } finally {
      metrics.record(key, System.nanoTime() - start, !succeeded);
      scope.close();
    }
  }

  @Override
  public InterceptorSlot slot() {
    return InterceptorSlot.TRACING;
  }
}
