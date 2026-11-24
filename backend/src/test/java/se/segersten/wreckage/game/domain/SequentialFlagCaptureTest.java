package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.api.PlayerResponse;
import se.segersten.wreckage.game.engine.MovementEngine;
import se.segersten.wreckage.game.support.SequentialFlagScenarios;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

class SequentialFlagCaptureTest {
    @Test
    void standingOnNewlyUnlockedFlagRequiresLeavingAndReentering() {
        Game game = fixture(Scenario.STANDING);
        command(game, 0, MovementOrder.FORWARD_1);
        assertOwnership(game, List.of("CP1"), List.of());
        command(game, 1, MovementOrder.REVERSE_1);
        assertOwnership(game, List.of("CP1"), List.of());
        command(game, 1, MovementOrder.FORWARD_1);
        assertOwnership(game, List.of("CP1"), List.of("CP2"));
        command(game, 0, MovementOrder.FORWARD_1);
        assertOwnership(game, List.of("CP1"), List.of("CP2"));
        assertThat(game.getPlayers().getFirst().getScore()).isEqualTo(2);
        assertThat(game.getPlayers().get(1).getScore()).isEqualTo(2);
    }

    @Test
    void onePushAwardsBothFlagsInNumericalOrderDespiteReversePushEventsAndStorageOrder() {
        for (Scenario scenario : List.of(Scenario.PUSH, Scenario.REVERSED_PUSH)) {
            Game game = fixture(scenario);
            command(game, 0, MovementOrder.FORWARD_1);
            assertOwnership(game, List.of("CP1"), List.of("CP2"));
            assertThat(game.getRound().playback()).extracting(RoundEvent::type).containsExactly(
                    RoundEventType.PUSH, RoundEventType.RAM, RoundEventType.SCORE_CHANGED, RoundEventType.SCORE_CHANGED);
            assertThat(captures(game.getRound())).extracting(RoundEvent::checkpointId).containsExactly("CP1", "CP2");
            assertThat(captures(game.getRound())).extracting(RoundEvent::playerId)
                    .containsExactly(game.getPlayers().getFirst().getId(), game.getPlayers().get(1).getId());
            for (Player player : game.getPlayers()) {
                var response = PlayerResponse.from(player, game.getBoard(), game.getPlayers());
                assertThat(response.capturedCheckpoints()).isOne();
                assertThat(response.nextCheckpoint()).isEqualTo("CP3");
            }
            assertThat(game.getRound().playback()).extracting(RoundEvent::sequence).containsExactly(1, 2, 3, 4);
        }
    }

    @Test
    void wallBlocksWholeChainWithoutAnyCapture() {
        Game game = fixture(Scenario.BLOCKED_PUSH);
        var before = game.getVehicleStates();
        command(game, 0, MovementOrder.FORWARD_1);
        assertOwnership(game, List.of(), List.of());
        assertThat(game.getVehicleStates()).isEqualTo(before);
        assertThat(captures(game.getRound())).isEmpty();
    }

    @Test
    void laserCapturesNextFlagsForTheDisplacedRobotNotTheShooter() {
        Game game = fixture(Scenario.LASER);
        command(game, 0, MovementOrder.LASER);
        assertOwnership(game, List.of(), List.of("CP1"));
        command(game, 0, MovementOrder.LASER);
        assertOwnership(game, List.of(), List.of("CP1", "CP2"));
        assertThat(game.getPlayers().getFirst().getScore()).isZero();
        assertThat(game.getPlayers().get(1).getScore()).isEqualTo(4);
    }

    @Test
    void conveyorCapturesTheNextGloballyAvailableFlag() {
        Game game = fixture(Scenario.CONVEYOR);
        game.tick(game.getContinuous().nextTick);
        assertOwnership(game, List.of("CP1"), List.of("CP2"));
        assertThat(captures(game.getRound())).singleElement().satisfies(event -> {
            assertThat(event.checkpointId()).isEqualTo("CP2");
            assertThat(event.newPosition()).isEqualTo(new Position(3, 1));
        });
    }

    @Test
    void laserAndConveyorPushChainsAlsoCaptureBothFlagsInNumericalOrder() {
        for (boolean conveyor : List.of(false, true)) {
            Player actor = Player.create(UUID.randomUUID(), "Actor", "a");
            Player first = Player.create(UUID.randomUUID(), "First", "b");
            Player second = Player.create(UUID.randomUUID(), "Second", "c");
            Board board = board(new Position(2, 1), new Position(3, 1),
                    conveyor ? List.of(new Conveyor(new Position(0, 1), Direction.EAST)) : List.of());
            Round round = resolve(board, List.of(actor, first, second),
                    List.of(state(second, 2, 1), state(first, 1, 1), state(actor, 0, 1)),
                    Map.of(actor.getId(), conveyor ? MovementOrder.WAIT : MovementOrder.LASER,
                            first.getId(), MovementOrder.WAIT, second.getId(), MovementOrder.WAIT));
            assertThat(first.getVisitedCheckpoints()).containsExactly("CP1");
            assertThat(second.getVisitedCheckpoints()).containsExactly("CP2");
            assertThat(actor.getVisitedCheckpoints()).isEmpty();
            assertThat(captures(round)).extracting(RoundEvent::checkpointId).containsExactly("CP1", "CP2");
        }
    }

    @Test
    void legacyMultiStepCommandCannotRetroactivelyClaimAnEarlierLockedEntry() {
        var player = Player.create(UUID.randomUUID(), "Alice", "a");
        Board board = board(new Position(2, 1), new Position(1, 1), List.of());
        Round round = resolve(board, List.of(player), List.of(state(player, 0, 1)),
                Map.of(player.getId(), MovementOrder.FORWARD_3));
        assertThat(player.getVisitedCheckpoints()).containsExactly("CP1");
        assertThat(captures(round)).extracting(RoundEvent::checkpointId).containsExactly("CP1");
    }

    @Test
    void separateConveyorsRetainMapOrderRatherThanFormingOneCaptureBatch() {
        for (boolean firstFlagMovesFirst : List.of(false, true)) {
            var alice = Player.create(UUID.randomUUID(), "Alice", "a");
            var bob = Player.create(UUID.randomUUID(), "Bob", "b");
            var first = new Conveyor(new Position(0, 2), Direction.EAST);
            var second = new Conveyor(new Position(0, 1), Direction.EAST);
            Board board = board(new Position(1, 2), new Position(1, 1),
                    firstFlagMovesFirst ? List.of(first, second) : List.of(second, first));
            Round round = resolve(board, List.of(alice, bob), List.of(state(alice, 0, 1), state(bob, 0, 2)),
                    Map.of(alice.getId(), MovementOrder.WAIT, bob.getId(), MovementOrder.WAIT));
            assertThat(bob.getVisitedCheckpoints()).containsExactly("CP1");
            assertThat(alice.getVisitedCheckpoints()).containsExactlyElementsOf(firstFlagMovesFirst ? List.of("CP2") : List.of());
            assertThat(captures(round)).hasSize(firstFlagMovesFirst ? 2 : 1);
        }
    }

    @Test
    void separateCommandsDoNotRetroactivelyCaptureLockedEntries() {
        var alice = Player.create(UUID.randomUUID(), "Alice", "a");
        var bob = Player.create(UUID.randomUUID(), "Bob", "b");
        Board board = board(new Position(1, 2), new Position(1, 1), List.of());
        resolve(board, List.of(alice, bob), List.of(state(alice, 0, 1), state(bob, 0, 2)),
                Map.of(alice.getId(), MovementOrder.FORWARD_1, bob.getId(), MovementOrder.FORWARD_1));
        assertThat(alice.getVisitedCheckpoints()).isEmpty();
        assertThat(bob.getVisitedCheckpoints()).containsExactly("CP1");
    }

    private Game fixture(Scenario scenario) {
        Game original = new Game(UUID.randomUUID(), new Board(10, 10));
        original.addPlayer("Alice");
        original.addPlayer("Bob");
        return SequentialFlagScenarios.configure(original, scenario);
    }

    private void command(Game game, int playerIndex, MovementOrder command) {
        game.getContinuous().queues.get(game.getPlayers().get(playerIndex).getId()).append(command);
        for (int tick = 0; tick < 3; tick++) game.tick(game.getContinuous().nextTick);
    }

    private void assertOwnership(Game game, List<String> alice, List<String> bob) {
        assertThat(game.getPlayers().getFirst().getVisitedCheckpoints()).containsExactlyInAnyOrderElementsOf(alice);
        assertThat(game.getPlayers().get(1).getVisitedCheckpoints()).containsExactlyInAnyOrderElementsOf(bob);
    }

    private List<RoundEvent> captures(Round round) {
        return round.playback().stream().filter(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT).toList();
    }

    private Board board(Position first, Position second, List<Conveyor> conveyors) {
        var base = new Board(8, 6);
        return new Board(8, 6, Set.of(), Set.of(), Set.of(new Checkpoint("CP1", 1, first),
                new Checkpoint("CP2", 2, second), new Checkpoint("CP3", 3, new Position(6, 4)),
                new Checkpoint("CP4", 4, new Position(7, 4))), base.spawnPoints(), conveyors, List.of());
    }

    private VehicleState state(Player player, int x, int y) {
        return new VehicleState(new Vehicle(UUID.randomUUID(), player.getId()), new Position(x, y), Direction.EAST);
    }

    private Round resolve(Board board, List<Player> players, List<VehicleState> states, Map<UUID, MovementOrder> orders) {
        Map<UUID, PlayerProgram> programs = new java.util.LinkedHashMap<>();
        players.forEach(player -> programs.put(player.getId(), new PlayerProgram(player.getId(), 1,
                List.of(orders.get(player.getId())), true)));
        Round round = new Round(1, programs, players.stream().map(Player::getId).toList(), new GameState(board, states));
        round.resolve(new MovementEngine(), players, GameConfiguration.defaults());
        return round;
    }
}
