package edu.ums.core.archfixturea.service;

import edu.ums.platform.invoke.Interceptor;
import edu.ums.platform.invoke.InterceptorSlot;
import edu.ums.platform.invoke.Invocation;

/** Violates INTERCEPTORS_ONLY_IN_INVOKE. */
public class RogueInterceptor implements Interceptor {
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    return invocation.proceed();
  }

  @Override
  public InterceptorSlot slot() {
    return InterceptorSlot.AUDIT;
  }
}
