package edu.ums.platform.invoke;

/** One link of the facade interceptor chain (Chain of Responsibility). */
public interface Interceptor {

  /**
   * Handles a call. Implementations normally do work around {@code invocation.proceed()}; they may
   * call it more than once (e.g. to retry) or not at all (to reject the call).
   *
   * @return the call's result
   * @throws Throwable whatever the rest of the chain or the target throws
   */
  Object intercept(Invocation invocation) throws Throwable;

  /** Returns the slot this interceptor occupies; it decides the position in the chain. */
  InterceptorSlot slot();
}
