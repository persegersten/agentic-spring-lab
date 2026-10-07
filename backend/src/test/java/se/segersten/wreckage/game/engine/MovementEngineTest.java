package se.segersten.wreckage.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.ActionType;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleStatus;
import se.segersten.wreckage.game.domain.VehicleTurn;
import se.segersten.wreckage.game.domain.Wall;

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
    void openBoardEdgeCrashesTheMovingVehicleImmediately() {
        VehicleState per = state(0, 6, Direction.NORTH);
        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(per, MovementOrder.FORWARD_1))),
                new GameState(board, List.of(per)));
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.CRASH);
        assertThat(result.events().getFirst().newPosition()).isEqualTo(new Position(0, 7));
        assertThat(result.state().vehicleStates()).singleElement()
                .extracting(VehicleState::status).isEqualTo(VehicleStatus.CRASHED);
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
    void waitLeavesAllVehiclesUnchangedWithoutEvents() {
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
    void forwardAndReverseCrashAtOpenBoardBoundary() {
        VehicleState northAtTop = state(0, 6, Direction.NORTH);
        VehicleState reversingSouthAtTop = state(1, 6, Direction.SOUTH);

        assertThat(resolve(List.of(northAtTop), order(northAtTop, MovementOrder.FORWARD_1))
                .vehicleStates().getFirst().status()).isEqualTo(VehicleStatus.CRASHED);
        assertThat(resolve(List.of(reversingSouthAtTop), order(reversingSouthAtTop, MovementOrder.REVERSE_1))
                .vehicleStates().getFirst().status()).isEqualTo(VehicleStatus.CRASHED);
    }

    @Test
    void wallBlocksMovementAcrossTheEdgeFromEitherSide() {
        Board walledBoard = new Board(7, 7,
                Set.of(new Wall(new Position(3, 3), Direction.EAST)));
        VehicleState eastbound = state(3, 3, Direction.EAST);
        VehicleState westbound = state(4, 3, Direction.WEST);

        var eastResult = engine.resolveTurnWithEvents(
                new Turn(List.of(order(eastbound, MovementOrder.FORWARD_1))),
                new GameState(walledBoard, List.of(eastbound)));
        var westResult = engine.resolveTurnWithEvents(
                new Turn(List.of(order(westbound, MovementOrder.FORWARD_1))),
                new GameState(walledBoard, List.of(westbound)));

        assertThat(eastResult.state().vehicleStates()).containsExactly(eastbound);
        assertThat(westResult.state().vehicleStates()).containsExactly(westbound);
        assertThat(eastResult.events()).isEmpty();
        assertThat(westResult.events()).isEmpty();
    }

    @Test
    void reverseMovementRespectsWallsWithoutChangingOrientation() {
        Board walledBoard = new Board(7, 7,
                Set.of(new Wall(new Position(2, 3), Direction.EAST)));
        VehicleState vehicle = state(3, 3, Direction.EAST);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(vehicle, MovementOrder.REVERSE_1))),
                new GameState(walledBoard, List.of(vehicle)));

        assertThat(result.state().vehicleStates()).containsExactly(vehicle);
        assertThat(result.events()).isEmpty();
    }

    @Test
    void forwardTwoKeepsItsSuccessfulFirstStepWhenSecondStepHitsAWall() {
        Board walledBoard = new Board(7, 7,
                Set.of(new Wall(new Position(2, 3), Direction.NORTH)));
        VehicleState vehicle = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(vehicle, MovementOrder.FORWARD_2))),
                new GameState(walledBoard, List.of(vehicle)));

        assertThat(result.state().vehicleStates()).containsExactly(
                new VehicleState(vehicle.vehicle(), new Position(2, 3), Direction.NORTH));
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.MOVE);
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
    void intermediateWallBlocksTheWholePushChainAtomically() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState middle = state(2, 2, Direction.SOUTH);
        VehicleState front = state(3, 2, Direction.NORTH);
        Board walledBoard = new Board(7, 7,
                Set.of(new Wall(new Position(2, 2), Direction.EAST)));
        GameState original = new GameState(walledBoard, List.of(moving, middle, front));

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))), original);

        assertThat(result.state()).isSameAs(original);
        assertThat(result.state().vehicleStates()).containsExactly(moving, middle, front);
        assertThat(result.events()).isEmpty();
    }

    @Test
    void wallAtFinalDisplacementBlocksTheWholePushChainAtomically() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState middle = state(2, 2, Direction.SOUTH);
        VehicleState front = state(3, 2, Direction.NORTH);
        Board walledBoard = new Board(7, 7,
                Set.of(new Wall(new Position(3, 2), Direction.EAST)));
        GameState original = new GameState(walledBoard, List.of(moving, middle, front));

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))), original);

        assertThat(result.state()).isSameAs(original);
        assertThat(result.state().vehicleStates()).containsExactly(moving, middle, front);
        assertThat(result.events()).isEmpty();
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
    void pushedVehicleCrashesThroughEveryOpenBoardBoundary() {
        assertPushedCrash(state(0, 5, Direction.NORTH), state(0, 6, Direction.WEST), MovementOrder.FORWARD_1);
        assertPushedCrash(state(5, 0, Direction.EAST), state(6, 0, Direction.NORTH), MovementOrder.FORWARD_1);
        assertPushedCrash(state(0, 1, Direction.SOUTH), state(0, 0, Direction.EAST), MovementOrder.FORWARD_1);
        assertPushedCrash(state(1, 0, Direction.WEST), state(0, 0, Direction.SOUTH), MovementOrder.FORWARD_1);
    }

    @Test
    void movingIntoPitCrashesImmediatelyAndForwardTwoStops() {
        Board pitBoard = new Board(7, 7, Set.of(), Set.of(new Position(2, 3)));
        VehicleState moving = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_2))),
                new GameState(pitBoard, List.of(moving)));

        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.CRASH);
        assertThat(result.state().vehicleStates().getFirst()).satisfies(crashed -> {
            assertThat(crashed.position()).isEqualTo(new Position(2, 3));
            assertThat(crashed.status()).isEqualTo(VehicleStatus.CRASHED);
        });
    }

    @Test
    void pushingVehicleIntoPitCrashesItAndMovesThePusher() {
        Board pitBoard = new Board(7, 7, Set.of(), Set.of(new Position(3, 2)));
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                new GameState(pitBoard, List.of(moving, pushed)));

        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.CRASH, RoundEventType.RAM);
        assertThat(result.state().vehicleStates()).filteredOn(VehicleState::isActive)
                .extracting(VehicleState::position).containsExactly(new Position(2, 2));
        assertThat(result.state().vehicleStates()).filteredOn(state -> !state.isActive())
                .extracting(VehicleState::position).containsExactly(new Position(3, 2));
    }

    @Test
    void chainPushCrashesFrontVehicleAndLeavesUniqueActivePositions() {
        Board pitBoard = new Board(7, 7, Set.of(), Set.of(new Position(4, 2)));
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState middle = state(2, 2, Direction.SOUTH);
        VehicleState front = state(3, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                new GameState(pitBoard, List.of(moving, middle, front)));

        assertThat(result.events()).extracting(event -> event.type()).containsExactly(
                RoundEventType.CRASH, RoundEventType.PUSH, RoundEventType.RAM);
        assertThat(result.state().vehicleStates()).filteredOn(VehicleState::isActive)
                .extracting(VehicleState::position).containsExactlyInAnyOrder(
                        new Position(2, 2), new Position(3, 2)).doesNotHaveDuplicates();
        assertThat(result.state().vehicleStates().stream().filter(s -> !s.isActive()).toList())
                .singleElement().extracting(VehicleState::position).isEqualTo(new Position(4, 2));
    }

    @Test
    void explicitOuterWallBlocksMoveAndPushWithoutCrashing() {
        Board walledBoard = new Board(7, 7,
                Set.of(new Wall(new Position(0, 6), Direction.NORTH)));
        VehicleState moving = state(0, 5, Direction.NORTH);
        VehicleState pushed = state(0, 6, Direction.EAST);

        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                new GameState(walledBoard, List.of(moving, pushed)));

        assertThat(result.events()).isEmpty();
        assertThat(result.state().vehicleStates()).containsExactly(moving, pushed);
        assertThat(result.state().vehicleStates()).allMatch(VehicleState::isActive);
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

    @Test
    void anchorBlocksAnyRequiredPushAtomicallyButNotItsOwnMovement() {
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.EAST);
        VehicleState anchored = state(3, 2, Direction.EAST);
        RegisterEffects effects = new RegisterEffects();
        effects.anchor(anchored.vehicle().id());
        GameState original = new GameState(board, List.of(moving, pushed, anchored));

        var blocked = engine.resolveTurnWithEvents(new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                original, effects);

        assertThat(blocked.state()).isSameAs(original);
        assertThat(blocked.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.PUSH_BLOCKED);
            assertThat(event.vehicleId()).isEqualTo(anchored.vehicle().id());
            assertThat(event.actionType()).isEqualTo(ActionType.ANCHOR);
        });

        var selfMove = engine.resolveTurnWithEvents(new Turn(List.of(order(anchored, MovementOrder.FORWARD_1))),
                new GameState(board, List.of(anchored)), effects);
        assertThat(selfMove.state().vehicleStates().getFirst().position()).isEqualTo(new Position(4, 2));
    }

    @Test
    void shieldDoesNotPreventAPushCrash() {
        VehicleState pusher = state(1, 2, Direction.EAST);
        VehicleState shielded = state(2, 2, Direction.NORTH);
        Board pitBoard = new Board(7, 7, Set.of(), Set.of(new Position(3, 2)));
        RegisterEffects effects = new RegisterEffects();
        effects.shield(shielded.vehicle().id());

        var result = engine.resolveTurnWithEvents(new Turn(List.of(order(pusher, MovementOrder.FORWARD_1))),
                new GameState(pitBoard, List.of(pusher, shielded)), effects);

        assertThat(result.state().vehicleStates()).filteredOn(v -> v.vehicle().id().equals(shielded.vehicle().id()))
                .singleElement().extracting(VehicleState::status).isEqualTo(VehicleStatus.CRASHED);
    }

    @Test void obstacleBlocksMovementAndPushChains() {
        Board obstacleBoard = new Board("test", "Test", 7, 7, Set.of(), Set.of(), Set.of(),
                board.spawnPoints(), List.of(), List.of(), Set.of(), Set.of(new Position(3, 2)));
        VehicleState moving = state(1, 2, Direction.EAST);
        VehicleState pushed = state(2, 2, Direction.NORTH);

        var result = engine.resolveTurnWithEvents(new Turn(List.of(order(moving, MovementOrder.FORWARD_1))),
                new GameState(obstacleBoard, List.of(moving, pushed)));

        assertThat(result.state().vehicleStates()).containsExactly(moving, pushed);
        assertThat(result.events()).extracting(event -> event.type()).containsExactly(RoundEventType.MOVE_BLOCKED);
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

    private void assertPushedCrash(
            VehicleState moving, VehicleState pushed, MovementOrder movementOrder) {
        var result = engine.resolveTurnWithEvents(
                new Turn(List.of(order(moving, movementOrder))),
                new GameState(board, List.of(moving, pushed)));

        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.CRASH, RoundEventType.RAM);
        assertThat(result.state().vehicleStates()).filteredOn(state -> !state.isActive()).hasSize(1);
        assertThat(result.state().vehicleStates()).filteredOn(VehicleState::isActive)
                .extracting(VehicleState::position).doesNotHaveDuplicates();
    }
}
