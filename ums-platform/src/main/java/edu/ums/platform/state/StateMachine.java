package edu.ums.platform.state;

import edu.ums.platform.error.GuardFailedException;
import edu.ums.platform.error.InvalidTransitionException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable finite state machine over enum states {@code S} with a transition context {@code C}.
 * Built once with {@link #builder(Class)}; {@link #fire} checks that the transition exists, runs
 * its guard, then its action. The machine is stateless and thread-safe: callers keep the current
 * state (persisted with compare-and-set) and pass it in.
 */
// Complexity: time O(1) per fire/allowedFrom (two EnumMap lookups); space O(|S|^2) worst case
public final class StateMachine<S extends Enum<S>, C> {

  private final Map<S, Map<S, Transition<S, C>>> transitions;
  private final Map<S, Set<S>> allowed;

  private StateMachine(Map<S, Map<S, Transition<S, C>>> transitions, Map<S, Set<S>> allowed) {
    this.transitions = transitions;
    this.allowed = allowed;
  }

  /** Starts a builder for states of {@code type}. */
  public static <S extends Enum<S>, C> Builder<S, C> builder(Class<S> type) {
    return new Builder<>(type);
  }

  /**
   * Moves from {@code current} to {@code target}.
   *
   * @return {@code target}, after the guard passed and the action completed
   * @throws InvalidTransitionException if no such transition is defined (details: from, to)
   * @throws GuardFailedException with the guard's code and details if the guard refuses
   */
  public S fire(S current, S target, C context) {
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(target, "target");
    Transition<S, C> transition = transitions.get(current).get(target);
    if (transition == null) {
      throw new InvalidTransitionException(Map.of("from", current, "to", target));
    }
    if (transition.guard().check(context) instanceof GuardResult.Fail refused) {
      throw new GuardFailedException(refused.code(), refused.details());
    }
    transition.action().apply(context);
    return target;
  }

  /** Returns the states reachable from {@code state} in one transition; unmodifiable. */
  public Set<S> allowedFrom(S state) {
    return allowed.get(Objects.requireNonNull(state, "state"));
  }

  /** One defined transition. */
  public record Transition<S extends Enum<S>, C>(S from, S to, Guard<C> guard, Action<C> action) {

    /** Rejects {@code null} components. */
    public Transition {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
      Objects.requireNonNull(guard, "guard");
      Objects.requireNonNull(action, "action");
    }
  }

  /** Collects transitions; {@link #build()} produces an independent immutable machine. */
  public static final class Builder<S extends Enum<S>, C> {

    private final Class<S> type;
    private final Map<S, Map<S, Transition<S, C>>> transitions;

    private Builder(Class<S> type) {
      this.type = Objects.requireNonNull(type, "type");
      this.transitions = new EnumMap<>(type);
    }

    /** Adds an unguarded transition without an action. */
    public Builder<S, C> transition(S from, S to) {
      return transition(from, to, Guard.always(), Action.none());
    }

    /** Adds a guarded transition without an action. */
    public Builder<S, C> transition(S from, S to, Guard<C> guard) {
      return transition(from, to, guard, Action.none());
    }

    /**
     * Adds a guarded transition with an action.
     *
     * @throws IllegalArgumentException if {@code from -> to} is already defined
     */
    public Builder<S, C> transition(S from, S to, Guard<C> guard, Action<C> action) {
      Transition<S, C> transition = new Transition<>(from, to, guard, action);
      Map<S, Transition<S, C>> targets =
          transitions.computeIfAbsent(from, k -> new EnumMap<>(type));
      if (targets.putIfAbsent(to, transition) != null) {
        throw new IllegalArgumentException("duplicate transition " + from + " -> " + to);
      }
      return this;
    }

    /** Builds the machine; later builder changes do not affect it. */
    public StateMachine<S, C> build() {
      Map<S, Map<S, Transition<S, C>>> table = new EnumMap<>(type);
      Map<S, Set<S>> allowed = new EnumMap<>(type);
      for (S state : type.getEnumConstants()) {
        Map<S, Transition<S, C>> targets = new EnumMap<>(type);
        targets.putAll(transitions.getOrDefault(state, Map.of()));
        table.put(state, Collections.unmodifiableMap(targets));
        Set<S> reachable = EnumSet.noneOf(type);
        reachable.addAll(targets.keySet());
        allowed.put(state, Collections.unmodifiableSet(reachable));
      }
      return new StateMachine<>(
          Collections.unmodifiableMap(table), Collections.unmodifiableMap(allowed));
    }
  }
}
