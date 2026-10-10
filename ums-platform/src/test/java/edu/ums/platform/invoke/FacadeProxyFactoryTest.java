package edu.ums.platform.invoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.UniqueElements;
import org.junit.jupiter.api.Test;

class FacadeProxyFactoryTest {

  private final FacadeProxyFactory factory = new FacadeProxyFactory();

  @Test
  void runsInterceptorsInSlotOrderRegardlessOfListOrder() {
    List<String> log = new ArrayList<>();
    List<Interceptor> shuffled = new ArrayList<>();
    for (InterceptorSlot slot : InterceptorSlot.values()) {
      shuffled.add(recording(slot, log));
    }
    Collections.shuffle(shuffled, new Random(42));

    String result = factory.create(DemoApi.class, new EchoApi(log), shuffled).read("x");

    assertThat(result).isEqualTo("read:x");
    List<String> expected = new ArrayList<>();
    Arrays.stream(InterceptorSlot.values()).forEach(s -> expected.add(s.name()));
    expected.add("target");
    assertThat(log).containsExactlyElementsOf(expected);
  }

  @Property(tries = 300)
  void anySubsetInAnyOrderRunsSortedThenTarget(
      @ForAll @UniqueElements @Size(max = 8) List<InterceptorSlot> slots, @ForAll long seed) {
    List<String> log = new ArrayList<>();
    List<Interceptor> interceptors = new ArrayList<>();
    slots.forEach(s -> interceptors.add(recording(s, log)));
    Collections.shuffle(interceptors, new Random(seed));

    String result = factory.create(DemoApi.class, new EchoApi(log), interceptors).write("y");

    List<String> expected = new ArrayList<>(slots.stream().sorted().map(Enum::name).toList());
    expected.add("target");
    assertThat(result).isEqualTo("write:y");
    assertThat(log).containsExactlyElementsOf(expected);
  }

  @Test
  void duplicateSlotIsRejected() {
    List<String> log = new ArrayList<>();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                factory.create(
                    DemoApi.class,
                    new EchoApi(log),
                    List.of(
                        recording(InterceptorSlot.TRACING, log),
                        recording(InterceptorSlot.TRACING, log))))
        .withMessageContaining("TRACING");
  }

  @Test
  void nonInterfaceIsRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.create(EchoApi.class, new EchoApi(new ArrayList<>()), List.of()));
  }

  @Test
  void objectMethodsBypassTheChain() {
    AtomicInteger calls = new AtomicInteger();
    Interceptor counting = counting(InterceptorSlot.TRACING, calls);
    DemoApi a = factory.create(DemoApi.class, new EchoApi(new ArrayList<>()), List.of(counting));
    DemoApi b = factory.create(DemoApi.class, new EchoApi(new ArrayList<>()), List.of(counting));

    assertThat(a).isEqualTo(a).isNotEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(a.hashCode());
    assertThat(a.toString()).isEqualTo("Facade[DemoApi]");
    assertThat(calls).hasValue(0);
  }

  @Test
  void proceedCanBeCalledAgainToRetry() {
    List<String> log = new ArrayList<>();
    Interceptor twice =
        new Interceptor() {
          @Override
          public Object intercept(Invocation invocation) throws Throwable {
            invocation.proceed();
            return invocation.proceed();
          }

          @Override
          public InterceptorSlot slot() {
            return InterceptorSlot.TRANSACTION;
          }
        };
    factory.create(DemoApi.class, new EchoApi(log), List.of(twice)).read("z");
    assertThat(log).containsExactly("target", "target");
  }

  @Test
  void argumentsAreCopiedAndAttributesAreShared() {
    Interceptor setter =
        new Interceptor() {
          @Override
          public Object intercept(Invocation invocation) throws Throwable {
            invocation.args()[0] = "tampered";
            invocation.attribute("seen", "yes");
            return invocation.proceed();
          }

          @Override
          public InterceptorSlot slot() {
            return InterceptorSlot.SESSION;
          }
        };
    Interceptor reader =
        new Interceptor() {
          @Override
          public Object intercept(Invocation invocation) throws Throwable {
            return invocation.attribute("seen").orElse("no") + "|" + invocation.proceed();
          }

          @Override
          public InterceptorSlot slot() {
            return InterceptorSlot.AUDIT;
          }
        };
    String result =
        factory
            .create(DemoApi.class, new EchoApi(new ArrayList<>()), List.of(reader, setter))
            .read("original");
    assertThat(result).isEqualTo("yes|read:original");
  }

  static Interceptor recording(InterceptorSlot slot, List<String> log) {
    return new Interceptor() {
      @Override
      public Object intercept(Invocation invocation) throws Throwable {
        log.add(slot.name());
        return invocation.proceed();
      }

      @Override
      public InterceptorSlot slot() {
        return slot;
      }
    };
  }

  static Interceptor counting(InterceptorSlot slot, AtomicInteger calls) {
    return new Interceptor() {
      @Override
      public Object intercept(Invocation invocation) throws Throwable {
        calls.incrementAndGet();
        return invocation.proceed();
      }

      @Override
      public InterceptorSlot slot() {
        return slot;
      }
    };
  }

  /** Target implementation that logs each call. */
  static final class EchoApi implements DemoApi {
    private final List<String> log;

    EchoApi(List<String> log) {
      this.log = log;
    }

    @Override
    public String read(String input) {
      log.add("target");
      return "read:" + input;
    }

    @Override
    public String write(String input) {
      log.add("target");
      return "write:" + input;
    }
  }
}
