package edu.ums.platform.state;

import static edu.ums.platform.state.TrafficLight.FLASHING;
import static edu.ums.platform.state.TrafficLight.GREEN;
import static edu.ums.platform.state.TrafficLight.RED;
import static edu.ums.platform.state.TrafficLight.YELLOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.ums.api.error.ErrorCode;
import edu.ums.platform.error.GuardFailedException;
import edu.ums.platform.error.InvalidTransitionException;
import edu.ums.platform.state.TrafficLight.Context;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateMachineTest {

  private final StateMachine<TrafficLight, Context> machine = TrafficLight.machine();

  static Stream<Arguments> everyPair() {
    return Stream.of(TrafficLight.values())
        .flatMap(from -> Stream.of(TrafficLight.values()).map(to -> Arguments.of(from, to)));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("everyPair")
  void everyPairIsEitherDefinedOrInvalid(TrafficLight from, TrafficLight to) {
    Context context = new Context(true, false);
    if (TrafficLight.DEFINED.contains(List.of(from, to))) {
      assertThat(machine.fire(from, to, context)).isEqualTo(to);
      assertThat(machine.allowedFrom(from)).contains(to);
    } else {
      assertThatThrownBy(() -> machine.fire(from, to, context))
          .isInstanceOfSatisfying(
              InvalidTransitionException.class,
              e -> {
                assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TRANSITION);
                assertThat(e.details()).isEqualTo(Map.of("from", from, "to", to));
              });
      assertThat(machine.allowedFrom(from)).doesNotContain(to);
      assertThat(context.log).isEmpty();
    }
  }

  @Test
  void allowedFromMatchesTheDefinitionAndIsUnmodifiable() {
    assertThat(machine.allowedFrom(RED)).containsExactlyInAnyOrder(GREEN, FLASHING);
    assertThat(machine.allowedFrom(GREEN)).containsExactlyInAnyOrder(YELLOW, FLASHING);
    assertThat(machine.allowedFrom(YELLOW)).containsExactlyInAnyOrder(RED, FLASHING);
    assertThat(machine.allowedFrom(FLASHING)).containsExactly(RED);
    assertThatThrownBy(() -> machine.allowedFrom(RED).add(YELLOW))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** Random event sequences never reach a state outside allowedFrom(previous). */
  @Property(tries = 1_000)
  void randomSequencesOnlyEverTakeAllowedTransitions(
      @ForAll @Size(max = 60) List<TrafficLight> targets, @ForAll boolean maintenance) {
    TrafficLight state = RED;
    Context context = new Context(maintenance, false);
    for (TrafficLight target : targets) {
      TrafficLight before = state;
      try {
        state = machine.fire(state, target, context);
        assertThat(machine.allowedFrom(before)).contains(state);
      } catch (InvalidTransitionException invalid) {
        assertThat(machine.allowedFrom(before)).doesNotContain(target);
        assertThat(state).isEqualTo(before);
      } catch (GuardFailedException refused) {
        assertThat(maintenance).isFalse();
        assertThat(target).isEqualTo(FLASHING);
        assertThat(state).isEqualTo(before);
      }
    }
    assertThat(state).isIn((Object[]) TrafficLight.values());
  }

  @Test
  void guardFailureLeavesStateUnchangedAndSkipsTheAction() {
    Context context = new Context(false, false);
    TrafficLight state = GREEN;

    assertThatThrownBy(() -> machine.fire(GREEN, FLASHING, context))
        .isInstanceOfSatisfying(
            GuardFailedException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.GUARD_FAILED);
              assertThat(e.details()).containsEntry("reason", "not_in_maintenance");
            });
    assertThat(state).isEqualTo(GREEN);
    assertThat(context.log).isEmpty();
  }

  @Test
  void actionRunsAfterGuardAndItsExceptionsPropagate() {
    Context ok = new Context(false, false);
    assertThat(machine.fire(YELLOW, RED, ok)).isEqualTo(RED);
    assertThat(machine.fire(FLASHING, RED, ok)).isEqualTo(RED);
    assertThat(ok.log).containsExactly("stop", "resume");

    assertThatThrownBy(() -> machine.fire(YELLOW, RED, new Context(false, true)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("action failed");
  }

  @Test
  void builderRejectsDuplicatesAndBuildIsIndependent() {
    StateMachine.Builder<TrafficLight, Context> builder =
        StateMachine.<TrafficLight, Context>builder(TrafficLight.class).transition(RED, GREEN);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.transition(RED, GREEN))
        .withMessageContaining("RED -> GREEN");

    StateMachine<TrafficLight, Context> built = builder.build();
    builder.transition(GREEN, YELLOW);
    assertThat(built.allowedFrom(GREEN)).isEmpty();
  }

  @Test
  void guardMayUseASpecificDomainCode() {
    StateMachine<TrafficLight, Context> strict =
        StateMachine.<TrafficLight, Context>builder(TrafficLight.class)
            .transition(RED, GREEN, c -> GuardResult.fail(ErrorCode.MARKS_INCOMPLETE))
            .build();
    assertThatThrownBy(() -> strict.fire(RED, GREEN, new Context(false, false)))
        .isInstanceOfSatisfying(
            GuardFailedException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.MARKS_INCOMPLETE));
  }
}
