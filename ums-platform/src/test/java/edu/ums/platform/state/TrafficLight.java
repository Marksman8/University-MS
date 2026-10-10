package edu.ums.platform.state;

import edu.ums.api.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo machine for the state-machine tests.
 *
 * <pre>
 *   RED -> GREEN -> YELLOW -> RED              (normal cycle; YELLOW -> RED logs "stop")
 *   RED | GREEN | YELLOW -> FLASHING           (guard: maintenance mode on)
 *   FLASHING -> RED                            (action: logs "resume")
 * </pre>
 */
enum TrafficLight {
  RED,
  GREEN,
  YELLOW,
  FLASHING;

  /** Every defined transition, as (from, to) pairs. */
  static final Set<List<TrafficLight>> DEFINED =
      Set.of(
          List.of(RED, GREEN),
          List.of(GREEN, YELLOW),
          List.of(YELLOW, RED),
          List.of(RED, FLASHING),
          List.of(GREEN, FLASHING),
          List.of(YELLOW, FLASHING),
          List.of(FLASHING, RED));

  /** Mutable test context: inputs to guards and a log written by actions. */
  static final class Context {
    final boolean maintenance;
    final boolean failAction;
    final List<String> log = new ArrayList<>();

    Context(boolean maintenance, boolean failAction) {
      this.maintenance = maintenance;
      this.failAction = failAction;
    }
  }

  static StateMachine<TrafficLight, Context> machine() {
    Guard<Context> maintenanceOnly =
        c ->
            c.maintenance
                ? GuardResult.pass()
                : GuardResult.fail(ErrorCode.GUARD_FAILED, Map.of("reason", "not_in_maintenance"));
    Action<Context> stop =
        c -> {
          if (c.failAction) {
            throw new IllegalStateException("action failed");
          }
          c.log.add("stop");
        };
    return StateMachine.<TrafficLight, Context>builder(TrafficLight.class)
        .transition(RED, GREEN)
        .transition(GREEN, YELLOW)
        .transition(YELLOW, RED, Guard.always(), stop)
        .transition(RED, FLASHING, maintenanceOnly)
        .transition(GREEN, FLASHING, maintenanceOnly)
        .transition(YELLOW, FLASHING, maintenanceOnly)
        .transition(FLASHING, RED, Guard.always(), c -> c.log.add("resume"))
        .build();
  }
}
