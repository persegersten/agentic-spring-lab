package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;
import se.segersten.wreckage.game.support.SequentialFlagScenarios;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

class FinalFlagCompletionTest {
    @Test
    void lastCaptureFinishesAllRegistersBoardEffectsAndRoundEndScoringBeforeCompletion() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        for (String flag : List.of("CP1", "CP2", "CP3")) alice.visitCheckpoint(flag);
        Board board = new Board(8, 6, Set.of(), Set.of(new Position(3, 1)), flags(),
                new Board(8, 6).spawnPoints(), List.of(new Conveyor(new Position(1, 3), Direction.EAST)),
                List.of(new Rotator(new Position(2, 3), Rotation.CLOCKWISE)), Set.of(new Position(2, 3)));
        Map<UUID, VehicleState> vehicles = states(alice, bob);
        Round round = new Round(1, Map.of(
                alice.getId(), program(alice, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.WAIT, MovementOrder.WAIT),
                bob.getId(), program(bob, MovementOrder.FORWARD_1, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT)),
                List.of(alice.getId(), bob.getId()), new GameState(board, List.copyOf(vehicles.values())));
        Game game = new Game(UUID.randomUUID(), List.of(alice, bob), board, GameStatus.RUNNING, vehicles, round);

        round.resolve(new MovementEngine(), game.getPlayers(), game.getConfiguration());
        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(round.playback()).filteredOn(event -> event.type() == RoundEventType.ROTATOR_TURN).hasSize(5);
        assertThat(round.playback()).filteredOn(event -> event.scoreReason() == ScoreChangeReason.CONTROL_POINT).hasSize(1);
        int captureIndex = indexOf(round, RoundEventType.SCORE_CHANGED);
        assertThat(round.playback().get(captureIndex).checkpointId()).isEqualTo("CP4");
        assertThat(indexOf(round, RoundEventType.CONVEYOR_MOVE)).isGreaterThan(captureIndex);
        assertThat(indexOf(round, RoundEventType.CRASH)).isGreaterThan(captureIndex);

        game.completeRound();
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getVehicleStates()).filteredOn(state -> state.vehicle().playerId().equals(alice.getId()))
                .singleElement().extracting(VehicleState::status).isEqualTo(VehicleStatus.CRASHED);
        assertThat(alice.getCapturedCheckpointCount(board)).isEqualTo(4);
        assertThat(bob.getScore()).isEqualTo(game.getConfiguration().controlPointScore());
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner).extracting(GamePlacement::playerId).containsExactly(alice.getId());
        assertThatThrownBy(() -> game.startRound(Instant.now())).isInstanceOf(IllegalStateException.class).hasMessage("The game is finished");
    }

    @Test
    void splitOwnershipCompletesOnlyAfterAllCommandsAndBoardEffectsInTheFinalTick() {
        Game game = fixture(Scenario.FINAL_TIE);
        queueFinalTick(game);
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getRound().playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.MOVE, RoundEventType.SCORE_CHANGED, RoundEventType.MOVE,
                RoundEventType.CONVEYOR_MOVE, RoundEventType.ROTATOR_TURN);
        assertThat(game.getPlacements()).extracting(GamePlacement::placement).containsExactly(1, 1);
        assertThat(game.getPlacements()).allMatch(GamePlacement::winner);
        assertThat(game.getVehicleStates()).filteredOn(state -> state.vehicle().playerId().equals(game.getPlayers().get(1).getId()))
                .singleElement().satisfies(state -> {
                    assertThat(state.position()).isEqualTo(new Position(2, 3));
                    assertThat(state.orientation()).isEqualTo(Direction.SOUTH);
                });
        Round finished = game.getRound();
        long tick = game.getContinuous().tick;
        long revision = game.getContinuous().revision;
        var vehicles = game.getVehicleStates();
        game.tick(game.getContinuous().nextTick);
        assertThat(game.getRound()).isSameAs(finished);
        assertThat(game.getContinuous().tick).isEqualTo(tick);
        assertThat(game.getContinuous().revision).isEqualTo(revision);
        assertThat(game.getVehicleStates()).isEqualTo(vehicles);
    }

    @Test
    void unequalFlagCountsBeatScoresCrashCountsAndPositions() {
        Game game = fixture(Scenario.FINAL_UNEQUAL);
        queueFinalTick(game);
        assertThat(game.getPlacements()).extracting(GamePlacement::checkpointsVisited).containsExactly(3, 1);
        assertThat(game.getPlacements()).extracting(GamePlacement::placement).containsExactly(1, 2);
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner).extracting(GamePlacement::playerId)
                .containsExactly(game.getPlayers().getFirst().getId());
        assertThat(game.getPlayers().getFirst().getScore()).isLessThan(game.getPlayers().get(1).getScore());
    }

    @Test
    void sharedRanksSkipPlacesAndIgnoreDistanceScoreAndCrashHistory() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        Player charlie = player("Charlie");
        alice.visitCheckpoint("CP1"); alice.visitCheckpoint("CP2");
        bob.visitCheckpoint("CP3"); bob.visitCheckpoint("CP4");
        alice.changeScore(-900); bob.changeScore(5000); charlie.changeScore(10000);
        for (int crash = 0; crash < 7; crash++) alice.recordCrash();
        Board board = new Board(8, 6, Set.of(), Set.of(), flags());
        Game game = new Game(UUID.randomUUID(), List.of(alice, bob, charlie), board, GameStatus.FINISHED,
                Map.of(alice.getId(), state(alice, 0, 0), bob.getId(), state(bob, 6, 5), charlie.getId(), state(charlie, 1, 1)), null);
        assertThat(game.getPlacements()).extracting(GamePlacement::placement).containsExactly(1, 1, 3);
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner).extracting(GamePlacement::playerId).containsExactly(alice.getId(), bob.getId());
        assertThat(game.getPlacements()).extracting(GamePlacement::distanceToNextCheckpoint).containsOnlyNulls();
    }

    @Test
    void finalConveyorCaptureDoesNotTruncateLaterConveyorsOrRotators() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        for (String flag : List.of("CP1", "CP2", "CP3")) alice.visitCheckpoint(flag);
        Board board = new Board(8, 6, Set.of(), Set.of(), flags(), new Board(8, 6).spawnPoints(),
                List.of(new Conveyor(new Position(0, 1), Direction.EAST), new Conveyor(new Position(0, 3), Direction.EAST)),
                List.of(new Rotator(new Position(1, 3), Rotation.CLOCKWISE)));
        Round round = new Round(1, Map.of(alice.getId(), program(alice, MovementOrder.WAIT), bob.getId(), program(bob, MovementOrder.WAIT)),
                List.of(alice.getId(), bob.getId()), new GameState(board, List.copyOf(states(alice, bob).values())));
        round.resolve(new MovementEngine(), List.of(alice, bob), GameConfiguration.defaults());
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.CONVEYOR_MOVE, RoundEventType.SCORE_CHANGED, RoundEventType.CONVEYOR_MOVE, RoundEventType.ROTATOR_TURN);
    }

    @Test
    void boardsWithoutFlagsDoNotFinishAutomatically() {
        Game game = new Game(UUID.randomUUID(), new Board(8, 6));
        game.addPlayer("Alice"); game.addPlayer("Bob");
        game.enableContinuous(Instant.EPOCH, 2000, 600000);
        game.tick(Instant.EPOCH.plusSeconds(2));
        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getContinuous().tick).isOne();
    }

    private void queueFinalTick(Game game) {
        for (Player player : game.getPlayers()) game.getContinuous().queues.get(player.getId()).append(MovementOrder.FORWARD_1);
        game.getContinuous().queues.get(game.getPlayers().getFirst().getId()).append(MovementOrder.TURN_RIGHT);
        for (int tick = 0; tick < 3; tick++) game.tick(game.getContinuous().nextTick);
    }
    private Game fixture(Scenario scenario) {
        Game original = new Game(UUID.randomUUID(), new Board(10, 10));
        original.addPlayer("Alice"); original.addPlayer("Bob");
        return SequentialFlagScenarios.configure(original, scenario);
    }
    private Set<Checkpoint> flags() {
        return Set.of(new Checkpoint("CP1", 1, new Position(6, 1)), new Checkpoint("CP2", 2, new Position(6, 2)),
                new Checkpoint("CP3", 3, new Position(6, 3)), new Checkpoint("CP4", 4, new Position(1, 1)));
    }
    private Player player(String name) { return Player.create(UUID.randomUUID(), name, "token"); }
    private VehicleState state(Player player, int x, int y) { return new VehicleState(new Vehicle(UUID.randomUUID(), player.getId()), new Position(x, y), Direction.EAST); }
    private Map<UUID, VehicleState> states(Player alice, Player bob) {
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        states.put(alice.getId(), state(alice, 0, 1)); states.put(bob.getId(), state(bob, 0, 3)); return states;
    }
    private PlayerProgram program(Player player, MovementOrder... commands) { return new PlayerProgram(player.getId(), commands.length, List.of(commands), true); }
    private int indexOf(Round round, RoundEventType type) { return java.util.stream.IntStream.range(0, round.playback().size()).filter(index -> round.playback().get(index).type() == type).findFirst().orElseThrow(); }
}
