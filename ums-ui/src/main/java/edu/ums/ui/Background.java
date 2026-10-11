package edu.ums.ui;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs backend calls off the JavaFX thread: a {@link Task} on a bounded executor (4 named daemon
 * threads, queue 64); results and errors come back on the FX thread through the task's handlers.
 * {@link #busy()} drives the spinner.
 */
// Complexity: time O(1) per submit; space O(64) queued tasks at most
public final class Background {

  private static final Logger LOG = LoggerFactory.getLogger(Background.class);

  private final ThreadPoolExecutor executor;
  private final AtomicInteger running = new AtomicInteger();
  private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);

  /** Creates the executor. */
  public Background() {
    AtomicInteger ids = new AtomicInteger();
    this.executor =
        new ThreadPoolExecutor(
            4,
            4,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            r -> {
              Thread t = new Thread(r, "ui-backend-" + ids.incrementAndGet());
              t.setDaemon(true);
              t.setUncaughtExceptionHandler(
                  (thread, e) -> LOG.error("Uncaught failure on {}", thread.getName(), e));
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  /** Whether any call is in flight (FX thread only). */
  public ReadOnlyBooleanProperty busy() {
    return busy.getReadOnlyProperty();
  }

  /** Runs {@code work} in the background; exactly one of the callbacks runs on the FX thread. */
  public <T> void run(Supplier<T> work, Consumer<T> onOk, Consumer<UmsApiException> onError) {
    Task<T> task =
        new Task<>() {
          @Override
          protected T call() {
            return work.get();
          }
        };
    task.setOnSucceeded(
        e -> {
          done();
          onOk.accept(task.getValue());
        });
    task.setOnFailed(
        e -> {
          done();
          onError.accept(asApi(task.getException()));
        });
    running.incrementAndGet();
    busy.set(true);
    try {
      executor.execute(task);
    } catch (java.util.concurrent.RejectedExecutionException full) {
      done();
      onError.accept(UmsApiException.of(ErrorCode.BUSY_TRY_AGAIN));
    }
  }

  private void done() {
    busy.set(running.decrementAndGet() > 0);
  }

  private static UmsApiException asApi(Throwable failure) {
    if (failure instanceof UmsApiException api) {
      return api;
    }
    LOG.error("Unexpected UI-side failure", failure);
    return UmsApiException.of(ErrorCode.INTERNAL_ERROR);
  }

  /** Stops the executor. */
  public void shutdown() {
    executor.shutdownNow();
  }
}
