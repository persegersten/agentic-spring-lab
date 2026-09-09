package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurnTest {

    @Test
    void rejectsMultipleOrdersForTheSameVehicle() {
        VehicleState state = state(new Vehicle(), 1, 1);

        assertThatThrownBy(() -> new Turn(List.of(
                new VehicleTurn(state, MovementOrder.FORWARD),
                new VehicleTurn(state, MovementOrder.TURN_LEFT))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A vehicle can only have one order per turn");
    }

    @Test
    void rejectsMultipleOrdersUsingDifferentStatesForTheSameVehicle() {
        Vehicle vehicle = new Vehicle();
        VehicleState firstState = state(vehicle, 1, 1);
        VehicleState secondState = state(vehicle, 2, 1);

        assertThatThrownBy(() -> new Turn(List.of(
                new VehicleTurn(firstState, MovementOrder.FORWARD),
                new VehicleTurn(secondState, MovementOrder.TURN_RIGHT))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A vehicle can only have one order per turn");
    }

    @Test
    void acceptsOneOrderForEachVehicle() {
        VehicleState first = state(new Vehicle(), 1, 1);
        VehicleState second = state(new Vehicle(), 2, 1);
        List<VehicleTurn> vehicleTurns = List.of(
                new VehicleTurn(first, MovementOrder.FORWARD),
                new VehicleTurn(second, MovementOrder.TURN_LEFT));

        Turn turn = new Turn(vehicleTurns);

        assertThat(turn.vehicleTurns()).containsExactlyElementsOf(vehicleTurns);
    }

    private VehicleState state(Vehicle vehicle, int x, int y) {
        return new VehicleState(vehicle, new Position(x, y), Direction.NORTH);
    }
}
