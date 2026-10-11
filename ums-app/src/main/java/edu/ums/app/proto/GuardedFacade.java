package edu.ums.app.proto;

import edu.ums.api.error.ErrorCode;
import edu.ums.api.error.UmsApiException;
import edu.ums.proto.audit.service.ActivityRecorder;
import edu.ums.proto.shared.Actor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outermost layer around a role facade: rejects calls after the session closed (SESSION_EXPIRED)
 * and records every failed or denied call in the activity log in a separate short transaction,
 * after the call's own transaction rolled back.
 */
final class GuardedFacade implements InvocationHandler {

  private final Object target;
  private final AtomicBoolean closed;
  private final Actor actor;
  private final ActivityRecorder activity;

  private GuardedFacade(
      Object target, AtomicBoolean closed, Actor actor, ActivityRecorder activity) {
    this.target = target;
    this.closed = closed;
    this.actor = actor;
    this.activity = activity;
  }

  static <T> T wrap(
      Class<T> api, T target, AtomicBoolean closed, Actor actor, ActivityRecorder activity) {
    Object proxy =
        Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            new GuardedFacade(target, closed, actor, activity));
    return api.cast(proxy);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(target, args);
    }
    if (closed.get()) {
      throw UmsApiException.of(ErrorCode.SESSION_EXPIRED);
    }
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException wrapped) {
      Throwable cause = wrapped.getCause();
      if (cause instanceof UmsApiException api) {
        activity.failure(actor, null, method.getName(), Map.of("code", api.code()));
      }
      throw cause;
    }
  }
}
