package se.segersten.wreckage.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleTurn;

class MovementEngineTest {

    private final MovementEngine engine = new MovementEngine();
    private final Board board = new Board(7, 7);

    @Test
    void createsMoveEventWithOldAndNewPosition() {
        VehicleState per = state(2, 3, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(per, MovementOrder.FORWARD))),
                new GameState(board, List.of(per)));

        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.MOVE);
            assertThat(event.playerId()).isEqualTo(per.vehicle().playerId());
            assertThat(event.oldPosition()).isEqualTo(new Position(2, 3));
            assertThat(event.newPosition()).isEqualTo(new Position(2, 4));
        });
    }

    @Test
    void createsTurnEventWithOldAndNewDirection() {
        VehicleState per = state(2, 3, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(per, MovementOrder.TURN_LEFT))),
                new GameState(board, List.of(per)));

        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.TURN);
            assertThat(event.oldDirection()).isEqualTo(Direction.NORTH);
            assertThat(event.newDirection()).isEqualTo(Direction.WEST);
            assertThat(event.oldPosition()).isEqualTo(event.newPosition());
        });
    }

    @Test
    void blockedMovementDoesNotCreateAnEvent() {
        VehicleState per = state(0, 6, Direction.NORTH);
        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(per, MovementOrder.FORWARD))),
                new GameState(board, List.of(per)));
        assertThat(result.events()).isEmpty();
        assertThat(result.state().vehicleStates()).containsExactly(per);
    }

    @Test
    void movesForwardNorthByIncreasingY() {
        VehicleState vehicle = state(3, 3, Direction.NORTH);

        GameState result = resolve(List.of(vehicle), order(vehicle, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(
                new VehicleState(vehicle.vehicle(), new Position(3, 4), Direction.NORTH));
    }

    @Test
    void reversesWithoutChangingOrientation() {
        VehicleState north = state(3, 3, Direction.NORTH);
        VehicleState east = state(3, 3, Direction.EAST);
        VehicleState south = state(3, 3, Direction.SOUTH);
        VehicleState west = state(3, 3, Direction.WEST);

        assertThat(resolve(List.of(north), order(north, MovementOrder.REVERSE)).vehicleStates())
                .containsExactly(new VehicleState(north.vehicle(), new Position(3, 2), Direction.NORTH));
        assertThat(resolve(List.of(east), order(east, MovementOrder.REVERSE)).vehicleStates())
                .containsExactly(new VehicleState(east.vehicle(), new Position(2, 3), Direction.EAST));
        assertThat(resolve(List.of(south), order(south, MovementOrder.REVERSE)).vehicleStates())
                .containsExactly(new VehicleState(south.vehicle(), new Position(3, 4), Direction.SOUTH));
        assertThat(resolve(List.of(west), order(west, MovementOrder.REVERSE)).vehicleStates())
                .containsExactly(new VehicleState(west.vehicle(), new Position(4, 3), Direction.WEST));
    }

    @Test
    void turnsWithoutChangingPosition() {
        VehicleState left = state(1, 1, Direction.NORTH);
        VehicleState right = state(3, 3, Direction.NORTH);

        GameState result = resolve(List.of(left, right),
                order(left, MovementOrder.TURN_LEFT),
                order(right, MovementOrder.TURN_RIGHT));

        assertThat(result.vehicleStates()).containsExactly(
                new VehicleState(left.vehicle(), left.position(), Direction.WEST),
                new VehicleState(right.vehicle(), right.position(), Direction.EAST));
    }

    @Test
    void blocksForwardAndReverseAtBoardBoundary() {
        VehicleState northAtTop = state(0, 6, Direction.NORTH);
        VehicleState reversingSouthAtTop = state(1, 6, Direction.SOUTH);

        assertThat(resolve(List.of(northAtTop),
                order(northAtTop, MovementOrder.FORWARD)).vehicleStates())
                .containsExactly(northAtTop);
        assertThat(resolve(List.of(reversingSouthAtTop),
                order(reversingSouthAtTop, MovementOrder.REVERSE)).vehicleStates())
                .containsExactly(reversingSouthAtTop);
    }

    @Test
    void blocksMovementIntoAnOccupiedPositionWithoutRamming() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState stationary = state(2, 2, Direction.NORTH);

        GameState result = resolve(List.of(moving, stationary),
                order(moving, MovementOrder.FORWARD));

        assertThat(result.vehicleStates()).containsExactly(moving, stationary);
    }

    @Test
    void resolvesCommandsInTheProvidedPlayerOrder() {
        VehicleState leader = state(2, 2, Direction.EAST);
        VehicleState follower = state(1, 2, Direction.EAST);

        GameState leaderFirst = resolve(List.of(follower, leader),
                order(leader, MovementOrder.FORWARD),
                order(follower, MovementOrder.FORWARD));
        GameState followerFirst = resolve(List.of(follower, leader),
                order(follower, MovementOrder.FORWARD),
                order(leader, MovementOrder.FORWARD));

        assertThat(leaderFirst.vehicleStates()).containsExactly(
                new VehicleState(follower.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(leader.vehicle(), new Position(3, 2), Direction.EAST));
        assertThat(followerFirst.vehicleStates()).containsExactly(
                follower,
                new VehicleState(leader.vehicle(), new Position(3, 2), Direction.EAST));
    }

    @Test
    void doesNotModifyTheInputState() {
        VehicleState vehicle = state(1, 1, Direction.NORTH);
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
