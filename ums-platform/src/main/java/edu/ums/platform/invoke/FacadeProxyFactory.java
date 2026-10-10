package edu.ums.platform.invoke;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wraps a facade implementation in a JDK dynamic proxy that runs every interface call through an
 * interceptor chain (Proxy + Chain of Responsibility). The chain is sorted by {@link
 * InterceptorSlot} once, at creation. {@code equals}, {@code hashCode} and {@code toString} bypass
 * the chain. Stateless and thread-safe; the proxies it returns are thread-safe too.
 */
// Complexity: time O(k log k) per create, O(k) per call; space O(k) — k interceptors, k <= 8
public final class FacadeProxyFactory {

  /**
   * Creates a proxy for {@code api}.
   *
   * @param interceptors any subset of slots, in any order
   * @throws IllegalArgumentException if {@code api} is not an interface or a slot repeats
   */
  public <T> T create(Class<T> api, T impl, List<Interceptor> interceptors) {
    Objects.requireNonNull(api, "api");
    Objects.requireNonNull(impl, "impl");
    if (!api.isInterface()) {
      throw new IllegalArgumentException(api.getName() + " is not an interface");
    }
    List<Interceptor> chain = sortedChain(interceptors);
    InvocationHandler handler = new Handler(api, impl, chain);
    return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, handler));
  }

  private static List<Interceptor> sortedChain(List<Interceptor> interceptors) {
    List<Interceptor> sorted = new ArrayList<>(interceptors);
    sorted.sort(Comparator.comparing(Interceptor::slot));
    Set<InterceptorSlot> seen = EnumSet.noneOf(InterceptorSlot.class);
    for (Interceptor interceptor : sorted) {
      if (!seen.add(interceptor.slot())) {
        throw new IllegalArgumentException("duplicate interceptor slot " + interceptor.slot());
      }
    }
    return List.copyOf(sorted);
  }

  private record Handler(Class<?> api, Object impl, List<Interceptor> chain)
      implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return objectMethod(proxy, method, args);
      }
      return new Invocation(impl, method, args, chain).proceed();
    }

    private Object objectMethod(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "Facade[" + api.getSimpleName() + "]";
        default -> throw new IllegalStateException("unexpected Object method " + method);
      };
    }
  }
}
