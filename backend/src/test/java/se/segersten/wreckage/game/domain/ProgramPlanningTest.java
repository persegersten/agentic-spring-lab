package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgramPlanningTest {
    @Test
    void loadoutEligibilityIncludesBothSideStepDirections() {
        var vehicle = new Vehicle(UUID.randomUUID(), UUID.randomUUID(), new Position(0, 0), Direction.NORTH,
                PrimaryWeapon.REPULSOR, SpecialAbility.SIDE_STEP);
        assertThat(vehicle.permits(ActionType.REPULSOR)).isTrue();
        assertThat(vehicle.permits(ActionType.SIDE_STEP_LEFT)).isTrue();
        assertThat(vehicle.permits(ActionType.SIDE_STEP_RIGHT)).isTrue();
        assertThat(vehicle.permits(ActionType.LASER)).isFalse();
        assertThat(vehicle.permits(ActionType.SHIELD)).isFalse();
    }

    @Test
    void supportsDraftsDuplicatesLockingAndTimeout() {
        UUID id = UUID.randomUUID();
        var program = PlayerProgram.dealt(id, 5, hand())
                .edit(List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1));
        var locked = program.lock(List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_2, MovementOrder.FORWARD_3, MovementOrder.REVERSE_1));
        assertThat(locked.ready()).isTrue();
        assertThatThrownBy(() -> locked.edit(List.of())).isInstanceOf(IllegalStateException.class);

        Instant deadline = Instant.parse("2099-01-01T00:00:00Z");
        var round = new Round(1, RoundPhase.PLANNING, Map.of(id, program), List.of(id),
                new GameState(new Board(5, 5), List.of()), List.of(), deadline);
        assertThat(round.completeTimedOutPrograms(deadline)).isTrue();
        assertThat(round.programs().get(id).commands()).containsExactly(
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_2,
                MovementOrder.FORWARD_3, MovementOrder.REVERSE_1);
    }

    @Test
    void exposesProgrammingCardCommandsAndKeepsWaitInternal() {
        assertThat(MovementOrder.values()).containsExactly(MovementOrder.FORWARD_1, MovementOrder.FORWARD_2,
                MovementOrder.FORWARD_3, MovementOrder.REVERSE_1, MovementOrder.TURN_LEFT,
                MovementOrder.TURN_RIGHT, MovementOrder.U_TURN, MovementOrder.LASER, MovementOrder.WAIT);
        assertThat(Arrays.stream(MovementOrder.values()).filter(MovementOrder::isProgrammingCard))
                .contains(MovementOrder.LASER).doesNotContain(MovementOrder.WAIT);
    }

    @Test
    void dealtHandsValidateProgramSizeCardCountsAndMultiplicity() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> PlayerProgram.dealt(id, 3, hand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 5");
        assertThatThrownBy(() -> PlayerProgram.dealt(id, 5, hand().subList(0, 7)))
                .isInstanceOf(IllegalArgumentException.class);

        var program = PlayerProgram.dealt(id, 5, hand());
        assertThat(program.hand()).hasSize(8);
        assertThatThrownBy(() -> program.lock(List.of(MovementOrder.FORWARD_1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> program.edit(List.of(
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Program cards must belong to the dealt hand");
        assertThatThrownBy(() -> program.edit(List.of(MovementOrder.WAIT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Program cards must belong to the dealt hand");
        assertThat(program.lock(List.of(MovementOrder.U_TURN, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_3, MovementOrder.TURN_LEFT, MovementOrder.FORWARD_1)).commands())
                .containsExactly(MovementOrder.U_TURN, MovementOrder.FORWARD_1,
                        MovementOrder.FORWARD_3, MovementOrder.TURN_LEFT, MovementOrder.FORWARD_1);
    }

    @Test
    void classifiesActionTimingServerSide() {
        assertThat(ActionType.values()).filteredOn(action -> action.timing() == ActionTiming.PRE_MOVEMENT)
                .containsExactly(ActionType.SHIELD, ActionType.ANCHOR);
        assertThat(ActionType.values()).filteredOn(action -> action.timing() == ActionTiming.POST_MOVEMENT)
                .containsExactly(ActionType.LASER, ActionType.REPULSOR, ActionType.ROCKET, ActionType.TURBO,
                        ActionType.SIDE_STEP_LEFT, ActionType.SIDE_STEP_RIGHT);
    }

    @Test
    void schedulesChangesAndClearsOneActionBeforeLock() {
        UUID id = UUID.randomUUID();
        var laser = new ScheduledAction(ActionType.LASER, 2);
        var repulsor = new ScheduledAction(ActionType.REPULSOR, 3);
        var program = PlayerProgram.dealt(id, 5, hand()).schedule(laser);
        assertThat(program.scheduledAction()).isEqualTo(laser);
        program = program.schedule(repulsor);
        assertThat(program.scheduledAction()).isEqualTo(repulsor);
        program = program.schedule(null);
        assertThat(program.scheduledAction()).isNull();
        assertThat(program.lock(hand().subList(0, 5)).scheduledAction()).isNull();
    }

    @Test
    void validatesActionRegisterAndLocksTheCompletePlan() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> PlayerProgram.dealt(id, 5, hand())
                .schedule(new ScheduledAction(ActionType.LASER, 6))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduledAction(ActionType.LASER, 0))
                .isInstanceOf(IllegalArgumentException.class);
        var locked = PlayerProgram.dealt(id, 5, hand()).lock(hand().subList(0, 5),
                new ScheduledAction(ActionType.SHIELD, 1));
        assertThatThrownBy(() -> locked.schedule(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> locked.edit(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void timeoutPreservesAnExplicitAction() {
        UUID id = UUID.randomUUID();
        Instant deadline = Instant.parse("2099-01-01T00:00:00Z");
        var action = new ScheduledAction(ActionType.ANCHOR, 2);
        var program = PlayerProgram.dealt(id, 5, hand()).schedule(action);
        var round = new Round(1, RoundPhase.PLANNING, Map.of(id, program), List.of(id),
                new GameState(new Board(5, 5), List.of()), List.of(), deadline);
        round.completeTimedOutPrograms(deadline);
        assertThat(round.programs().get(id).scheduledAction()).isEqualTo(action);
        assertThat(round.programs().get(id).ready()).isTrue();
        assertThat(round.programs().get(id).commands()).hasSize(5).allMatch(program.hand()::contains);
    }

    private List<MovementOrder> hand() {
        return List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_2,
                MovementOrder.FORWARD_3, MovementOrder.REVERSE_1, MovementOrder.TURN_LEFT,
                MovementOrder.TURN_RIGHT, MovementOrder.U_TURN);
    }
}
