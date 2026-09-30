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
    @Test void waitDoesNothing() {
        VehicleState vehicle = state(2, 2, Direction.NORTH);
        GameState initial = new GameState(board, List.of(vehicle));
        assertThat(engine.resolveTurn(new Turn(List.of(order(vehicle, MovementOrder.WAIT))), initial)).isEqualTo(initial);
    }

    private final MovementEngine engine = new MovementEngine();
    private final Board board = new Board(7, 7);

    @Test
    void createsMoveEventWithOldAndNewPosition() {
        VehicleState per = state(2, 3, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(per, MovementOrder.FORWARD_1))),
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
    void forwardTwoCreatesTwoSequentialMovementEvents() {
        VehicleState vehicle = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(vehicle, MovementOrder.FORWARD_2))),
                new GameState(board, List.of(vehicle)));

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).oldPosition()).isEqualTo(new Position(2, 2));
        assertThat(result.events().get(0).newPosition()).isEqualTo(new Position(2, 3));
        assertThat(result.events().get(1).oldPosition()).isEqualTo(new Position(2, 3));
        assertThat(result.events().get(1).newPosition()).isEqualTo(new Position(2, 4));
    }

    @Test
    void forwardTwoResolvesInteractionsSeparatelyAtEachStep() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_2))),
                new GameState(board, List.of(moving, pushed)));

        assertThat(result.events()).extracting(event -> event.type()).containsExactly(
                RoundEventType.PUSH, RoundEventType.RAM,
                RoundEventType.PUSH, RoundEventType.RAM);
        assertThat(result.events().get(1).newPosition()).isEqualTo(result.events().get(3).oldPosition());
    }

    @Test
    void uTurnReversesEveryOrientationWithoutMoving() {
        for (Direction direction : Direction.values()) {
            VehicleState vehicle = state(2, 2, direction);
            var result = engine.resolveTurnWithEvents(
                    new Turn(List.of(order(vehicle, MovementOrder.U_TURN))),
                    new GameState(board, List.of(vehicle)));

            assertThat(result.events()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(RoundEventType.TURN);
                assertThat(event.oldDirection()).isEqualTo(direction);
                assertThat(event.newDirection()).isEqualTo(direction.reverse());
                assertThat(event.oldPosition()).isEqualTo(event.newPosition());
            });
        }
    }

    @Test
    void blockedMovementDoesNotCreateAnEvent() {
        VehicleState per = state(0, 6, Direction.NORTH);
        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(per, MovementOrder.FORWARD_1))),
                new GameState(board, List.of(per)));
        assertThat(result.events()).isEmpty();
        assertThat(result.state().vehicleStates()).containsExactly(per);
    }

    @Test
    void movesForwardNorthByIncreasingY() {
        VehicleState vehicle = state(3, 3, Direction.NORTH);

        GameState result = resolve(List.of(vehicle), order(vehicle, MovementOrder.FORWARD_1));

        assertThat(result.vehicleStates()).containsExactly(
                new VehicleState(vehicle.vehicle(), new Position(3, 4), Direction.NORTH));
    }

    @Test
    void reversesWithoutChangingOrientation() {
        VehicleState north = state(3, 3, Direction.NORTH);
        VehicleState east = state(3, 3, Direction.EAST);
        VehicleState south = state(3, 3, Direction.SOUTH);
        VehicleState west = state(3, 3, Direction.WEST);

        assertThat(resolve(List.of(north), order(north, MovementOrder.REVERSE_1)).vehicleStates())
                .containsExactly(new VehicleState(north.vehicle(), new Position(3, 2), Direction.NORTH));
        assertThat(resolve(List.of(east), order(east, MovementOrder.REVERSE_1)).vehicleStates())
                .containsExactly(new VehicleState(east.vehicle(), new Position(2, 3), Direction.EAST));
        assertThat(resolve(List.of(south), order(south, MovementOrder.REVERSE_1)).vehicleStates())
                .containsExactly(new VehicleState(south.vehicle(), new Position(3, 4), Direction.SOUTH));
        assertThat(resolve(List.of(west), order(west, MovementOrder.REVERSE_1)).vehicleStates())
                .containsExactly(new VehicleState(west.vehicle(), new Position(4, 3), Direction.WEST));
    }

    @Test
    void malfunctionNoOpLeavesAllVehiclesUnchangedWithoutEvents() {
        VehicleState moving = state(3, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.SOUTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.WAIT))),
                new GameState(board, List.of(moving, pushed)));

        assertThat(result.state().vehicleStates()).containsExactly(moving, pushed);
        assertThat(result.events()).isEmpty();
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
        // Feature 4 will replace this temporary blocking behavior with crashes.
        VehicleState northAtTop = state(0, 6, Direction.NORTH);
        VehicleState reversingSouthAtTop = state(1, 6, Direction.SOUTH);

        assertThat(resolve(List.of(northAtTop),
                order(northAtTop, MovementOrder.FORWARD_1)).vehicleStates())
                .containsExactly(northAtTop);
        assertThat(resolve(List.of(reversingSouthAtTop),
                order(reversingSouthAtTop, MovementOrder.REVERSE_1)).vehicleStates())
                .containsExactly(reversingSouthAtTop);
    }

    @Test
    void pushesAnotherVehicleAndCreatesPushThenRamEvents() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState stationary = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                new GameState(board, List.of(moving, stationary)));

        assertThat(result.state().vehicleStates()).containsExactly(
                new VehicleState(moving.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(stationary.vehicle(), new Position(3, 2), Direction.NORTH));
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.PUSH, RoundEventType.RAM);
        assertThat(result.events().get(0)).satisfies(event -> {
            assertThat(event.vehicleId()).isEqualTo(stationary.vehicle().id());
            assertThat(event.playerId()).isEqualTo(stationary.vehicle().playerId());
            assertThat(event.oldPosition()).isEqualTo(new Position(2, 2));
            assertThat(event.newPosition()).isEqualTo(new Position(3, 2));
        });
        assertThat(result.events().get(1)).satisfies(event -> {
            assertThat(event.vehicleId()).isEqualTo(moving.vehicle().id());
            assertThat(event.playerId()).isEqualTo(moving.vehicle().playerId());
            assertThat(event.oldPosition()).isEqualTo(new Position(1, 2));
            assertThat(event.newPosition()).isEqualTo(new Position(2, 2));
        });
    }

    @Test
    void pushesAChainFrontToBackWithoutChangingOrientations() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState middle = state(2, 2, Direction.SOUTH);
        VehicleState front = state(3, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                new GameState(board, List.of(moving, middle, front)));

        assertThat(result.state().vehicleStates()).containsExactly(
                new VehicleState(moving.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(middle.vehicle(), new Position(3, 2), Direction.SOUTH),
                new VehicleState(front.vehicle(), new Position(4, 2), Direction.NORTH));
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.PUSH, RoundEventType.PUSH, RoundEventType.RAM);
        assertThat(result.events()).extracting(event -> event.vehicleId())
                .containsExactly(front.vehicle().id(), middle.vehicle().id(), moving.vehicle().id());
        assertThat(result.state().vehicleStates()).extracting(VehicleState::position)
                .doesNotHaveDuplicates();
    }

    @Test
    void reverseCanPushAnotherVehicle() {
        VehicleState moving = state(3, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.SOUTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.REVERSE_1))),
                new GameState(board, List.of(moving, pushed)));

        assertThat(result.state().vehicleStates()).containsExactly(
                new VehicleState(moving.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(pushed.vehicle(), new Position(1, 2), Direction.SOUTH));
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.PUSH, RoundEventType.RAM);
    }

    @Test
    void blocksWholePushChainAtEveryBoardBoundary() {
        // Feature 4 will replace this temporary blocking behavior with crashes.
        assertBlockedPush(state(0, 5, Direction.NORTH), state(0, 6, Direction.WEST), MovementOrder.FORWARD_1);
        assertBlockedPush(state(5, 0, Direction.EAST), state(6, 0, Direction.NORTH), MovementOrder.FORWARD_1);
        assertBlockedPush(state(0, 1, Direction.SOUTH), state(0, 0, Direction.EAST), MovementOrder.FORWARD_1);
        assertBlockedPush(state(1, 0, Direction.WEST), state(0, 0, Direction.SOUTH), MovementOrder.FORWARD_1);
    }

    @Test
    void rammingIsDeterministicAndDoesNotModifyInputState() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.NORTH);
        GameState original = new GameState(board, List.of(moving, pushed));
        Turn turn = new Turn(List.of(order(moving, MovementOrder.FORWARD_1)));

        var first = engine.resolveTurnWithEvents(turn, original);
        var second = engine.resolveTurnWithEvents(turn, original);

        assertThat(first).isEqualTo(second);
        assertThat(original.vehicleStates()).containsExactly(moving, pushed);
    }

    @Test
    void resolvesCommandsInTheProvidedPlayerOrder() {
        VehicleState leader = state(2, 2, Direction.EAST);
        VehicleState follower = state(1, 2, Direction.EAST);

        GameState leaderFirst = resolve(List.of(follower, leader),
                order(leader, MovementOrder.FORWARD_1),
                order(follower, MovementOrder.FORWARD_1));
        GameState followerFirst = resolve(List.of(follower, leader),
                order(follower, MovementOrder.FORWARD_1),
                order(leader, MovementOrder.FORWARD_1));

        assertThat(leaderFirst.vehicleStates()).containsExactly(
                new VehicleState(follower.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(leader.vehicle(), new Position(3, 2), Direction.EAST));
        assertThat(followerFirst.vehicleStates()).containsExactly(
                new VehicleState(follower.vehicle(), new Position(2, 2), Direction.EAST),
                new VehicleState(leader.vehicle(), new Position(4, 2), Direction.EAST));
    }

    @Test
    void doesNotModifyTheInputState() {
        VehicleState vehicle = state(1, 1, Direction.NORTH);
        GameState original = new GameState(board, List.of(vehicle));

        engine.resolveTurn(new Turn(List.of(order(vehicle, MovementOrder.FORWARD_1))), original);

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

    private void assertBlockedPush(
            VehicleState moving, VehicleState pushed, MovementOrder movementOrder) {
        GameState original = new GameState(board, List.of(moving, pushed));

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, movementOrder))), original);

        assertThat(result.state()).isSameAs(original);
        assertThat(result.state().vehicleStates()).containsExactly(moving, pushed);
        assertThat(result.events()).isEmpty();
    }
}
