package edu.ums.platform.invoke;

import edu.ums.api.annotation.Writes;
import edu.ums.platform.db.TransactionRunner;
import java.util.Objects;

/**
 * Innermost link: runs the call in a transaction via {@link TransactionRunner} — read-write when
 * the facade method carries {@link Writes}, read-only otherwise. Retry on conflict is owned by the
 * runner (outermost transaction only); this interceptor adds no retry of its own, so a call that
 * joins an outer transaction is never retried by itself.
 */
// Complexity: time O(1) per call plus the runner's O(1) overhead; space O(1)
public final class TransactionInterceptor implements Interceptor {

  private final TransactionRunner transactions;

  /** Creates the interceptor. */
  public TransactionInterceptor(TransactionRunner transactions) {
    this.transactions = Objects.requireNonNull(transactions, "transactions");
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    boolean readOnly = invocation.annotation(Writes.class).isEmpty();
    try {
      return transactions.inTransaction(readOnly, () -> proceed(invocation));
    } catch (CheckedFailure carrier) {
      throw carrier.getCause();
    }
  }

  @Override
  public InterceptorSlot slot() {
    return InterceptorSlot.TRANSACTION;
  }

  /** Runs the rest of the chain; unchecked failures pass through so retry and rollback see them. */
  private static Object proceed(Invocation invocation) {
    try {
      return invocation.proceed();
    } catch (Throwable failure) { // CHECKSTYLE:ALLOW-CATCH transaction boundary: carry checked
      if (failure instanceof RuntimeException unchecked) {
        throw unchecked;
      }
      if (failure instanceof Error error) {
        throw error;
      }
      throw new CheckedFailure(failure);
    }
  }

  /** Carries a checked exception through the runner's {@code Supplier}; unwrapped above. */
  private static final class CheckedFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    CheckedFailure(Throwable cause) {
      super(cause);
    }
  }
}
