package edu.ums.platform.concurrent;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates threads named {@code <prefix>-<n>} with a fixed daemon flag and an uncaught-exception
 * handler that logs at ERROR and forwards to an {@link UncaughtErrorSink}.
 */
// Complexity: time O(1) per thread; space O(1)
public final class NamedThreadFactory implements ThreadFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NamedThreadFactory.class);

  private final String prefix;
  private final boolean daemon;
  private final UncaughtErrorSink sink;
  private final AtomicInteger counter = new AtomicInteger();

  /** Creates a factory for threads named {@code prefix-1}, {@code prefix-2}, ... */
  public NamedThreadFactory(String prefix, boolean daemon, UncaughtErrorSink sink) {
    this.prefix = Objects.requireNonNull(prefix, "prefix");
    this.daemon = daemon;
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  @Override
  public Thread newThread(Runnable task) {
    Thread thread = new Thread(task, prefix + "-" + counter.incrementAndGet());
    thread.setDaemon(daemon);
    thread.setUncaughtExceptionHandler(this::uncaught);
    return thread;
  }

  private void uncaught(Thread thread, Throwable error) {
    LOG.error("Uncaught failure on thread {}", thread.getName(), error);
    try {
      sink.record(thread.getName(), error);
    } catch (RuntimeException sinkFailure) { // CHECKSTYLE:ALLOW-CATCH best-effort error sink
      LOG.error("UncaughtErrorSink failed for thread {}", thread.getName(), sinkFailure);
    }
  }
}
