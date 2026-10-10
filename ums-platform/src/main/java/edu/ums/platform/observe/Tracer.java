package edu.ums.platform.observe;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Minimal tracer: one {@link TraceContext} per outer facade call, held in a ThreadLocal and copied
 * to the SLF4J MDC as {@code traceId}. Nested calls join the outer trace. The ThreadLocal and the
 * MDC entry are always removed when the outermost scope closes.
 */
// Complexity: time O(1) per open/close; space O(1) per thread
public final class Tracer {

  /** MDC key carrying the trace id on every log line. */
  public static final String MDC_TRACE_ID = "traceId";

  private final ThreadLocal<TraceContext> current = new ThreadLocal<>();

  /** Opens a scope: a new trace if none is active on this thread, otherwise joins it. */
  public Scope open() {
    if (current.get() != null) {
      return new Scope(false);
    }
    TraceContext context = new TraceContext(UUID.randomUUID());
    current.set(context);
    MDC.put(MDC_TRACE_ID, context.traceId().toString());
    return new Scope(true);
  }

  /** Returns the active trace on this thread, if any. */
  public Optional<TraceContext> current() {
    return Optional.ofNullable(current.get());
  }

  /** A trace scope; closing the outermost one clears the thread's trace. Not thread-safe. */
  public final class Scope implements AutoCloseable {

    private final boolean outermost;
    private boolean closed;

    private Scope(boolean outermost) {
      this.outermost = outermost;
    }

    /** Returns {@code true} if this scope started the trace. */
    public boolean isOutermost() {
      return outermost;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (outermost) {
        current.remove();
        MDC.remove(MDC_TRACE_ID);
      }
    }
  }
}
