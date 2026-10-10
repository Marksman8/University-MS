package edu.ums.platform.invoke;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One facade call travelling through the interceptor chain. Each interceptor receives the
 * invocation positioned after itself, so {@link #proceed()} always continues with the next
 * interceptor and may safely be called again (retry). Arguments are copied on entry. Attributes are
 * shared by every interceptor of the same call. Used by one thread only.
 */
// Complexity: time O(1) per proceed (k <= 8 links per call); space O(1) per link
public final class Invocation {

  private static final Object[] NO_ARGS = new Object[0];

  private final Object target;
  private final Method method;
  private final Object[] args;
  private final List<Interceptor> chain;
  private final int position;
  private final Map<String, Object> attributes;

  Invocation(Object target, Method method, Object[] args, List<Interceptor> chain) {
    this(target, method, args == null ? NO_ARGS : args.clone(), chain, 0, new HashMap<>(8));
  }

  private Invocation(
      Object target,
      Method method,
      Object[] args,
      List<Interceptor> chain,
      int position,
      Map<String, Object> attributes) {
    this.target = target;
    this.method = method;
    this.args = args;
    this.chain = chain;
    this.position = position;
    this.attributes = attributes;
  }

  /** Returns the facade interface that declares the called method. */
  public Class<?> declaringType() {
    return method.getDeclaringClass();
  }

  /** Returns the called method's name. */
  public String methodName() {
    return method.getName();
  }

  /** Returns the called interface method's annotation of {@code type}, if present. */
  public <A extends Annotation> Optional<A> annotation(Class<A> type) {
    return Optional.ofNullable(method.getAnnotation(type));
  }

  /** Returns a copy of the call's arguments. */
  public Object[] args() {
    return args.clone();
  }

  /** Returns the attribute set by an earlier interceptor of this call, if any. */
  public Optional<Object> attribute(String name) {
    return Optional.ofNullable(attributes.get(name));
  }

  /** Sets an attribute visible to later interceptors of this call. */
  public void attribute(String name, Object value) {
    attributes.put(name, value);
  }

  /**
   * Runs the rest of the chain, then the target method.
   *
   * @return the target's result
   * @throws Throwable the target's own exception (unwrapped), or one thrown by an interceptor
   */
  public Object proceed() throws Throwable {
    if (position < chain.size()) {
      Invocation next = new Invocation(target, method, args, chain, position + 1, attributes);
      return chain.get(position).intercept(next);
    }
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
