package se.segersten.wreckage.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleTurn;

class MovementEngineTest {

    private final MovementEngine engine = new MovementEngine();
    private final Board board = new Board(5, 5);

    @Test
    void movesForwardToAnUnoccupiedPosition() {
        VehicleState vehicle = state(1, 1, Direction.EAST);

        GameState result = resolve(List.of(vehicle), order(vehicle, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(
                new VehicleState(vehicle.vehicle(), new Position(2, 1), Direction.EAST));
    }

    @Test
    void turnsWithoutChangingPosition() {
        VehicleState left = state(1, 1, Direction.NORTH);
        VehicleState right = state(3, 3, Direction.SOUTH);

        GameState result = resolve(List.of(left, right),
                order(left, MovementOrder.TURN_LEFT),
                order(right, MovementOrder.TURN_RIGHT));

        assertThat(result.vehicleStates()).containsExactly(
                new VehicleState(left.vehicle(), left.position(), Direction.WEST),
                new VehicleState(right.vehicle(), right.position(), Direction.WEST));
    }

    @Test
    void blocksMovementOutsideBoard() {
        VehicleState vehicle = state(0, 0, Direction.NORTH);

        assertThat(resolve(List.of(vehicle), order(vehicle, MovementOrder.FORWARD)).vehicleStates())
                .containsExactly(vehicle);
    }

    @Test
    void vehicleWithoutOrderRemainsStationaryAndBlocksAnIncomingVehicle() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState stationary = state(2, 2, Direction.NORTH);

        GameState result = resolve(List.of(moving, stationary),
                order(moving, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(moving, stationary);
    }

    @Test
    void blocksAllVehiclesMovingToTheSamePosition() {
        VehicleState fromLeft = state(1, 2, Direction.EAST);
        VehicleState fromRight = state(3, 2, Direction.WEST);

        GameState result = resolve(List.of(fromLeft, fromRight),
                order(fromRight, MovementOrder.FORWARD),
                order(fromLeft, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(fromLeft, fromRight);
    }

    @Test
    void blocksVehiclesSwappingPositions() {
        VehicleState left = state(1, 2, Direction.EAST);
        VehicleState right = state(2, 2, Direction.WEST);

        GameState result = resolve(List.of(left, right),
                order(left, MovementOrder.FORWARD),
                order(right, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(left, right);
    }

    @Test
    void allowsFollowingIntoAPositionThatIsSuccessfullyVacated() {
        VehicleState follower = state(1, 2, Direction.EAST);
        VehicleState leader = state(2, 2, Direction.EAST);

        GameState result = resolve(List.of(follower, leader),
                order(follower, MovementOrder.FORWARD),
                order(leader, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(
                new VehicleState(follower.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(leader.vehicle(), new Position(3, 2), Direction.EAST));
    }

    @Test
    void propagatesBlockedMovementBackThroughAChain() {
        VehicleState first = state(0, 2, Direction.EAST);
        VehicleState second = state(1, 2, Direction.EAST);
        VehicleState third = state(2, 2, Direction.NORTH);

        GameState result = resolve(List.of(first, second, third),
                order(first, MovementOrder.FORWARD),
                order(second, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(first, second, third);
    }

    @Test
    void doesNotModifyTheInputState() {
        VehicleState vehicle = state(1, 1, Direction.EAST);
        GameState original = new GameState(board, List.of(vehicle));

        engine.resolveTurn(new Turn(List.of(order(vehicle, MovementOrder.FORWARD))), original);

        assertThat(original.vehicleStates()).containsExactly(vehicle);
    }

    private GameState resolve(List<VehicleState> states, VehicleTurn... turns) {
        return engine.resolveTurn(new Turn(List.of(turns)), new GameState(board, states));
    }

    private VehicleTurn order(VehicleState state, MovementOrder order) {
        return new VehicleTurn(state, order);
    }

    private VehicleState state(int x, int y, Direction direction) {
        return new VehicleState(new Vehicle(), new Position(x, y), direction);
    }
}
