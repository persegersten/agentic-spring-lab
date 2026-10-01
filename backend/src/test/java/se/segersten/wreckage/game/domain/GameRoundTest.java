package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;

class GameRoundTest {
    @Test
    void startsEmptyProgramsAndResolvesCompleteOnes() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(3, now);
        Player player = game.addPlayer("Alice", "a");

        Round round = game.startRound(now);
        assertThat(round.programs().get(player.getId()).commands()).isEmpty();
        assertThat(round.initiative()).containsExactly(player.getId());

        round.lock(player.getId(), List.of(
                MovementOrder.FORWARD_1, MovementOrder.TURN_RIGHT, MovementOrder.WAIT));
        round.resolve(new MovementEngine());

        assertThat(round.phase()).isEqualTo(RoundPhase.PLAYBACK);
    }

    @Test
    void rotatesInitiativeLeftExactlyOnceWhenEachNewRoundStarts() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(1, now);
        List<UUID> joined = List.of(
                game.addPlayer("A", "a").getId(),
                game.addPlayer("B", "b").getId(),
                game.addPlayer("C", "c").getId(),
                game.addPlayer("D", "d").getId());

        Round first = game.startRound(now);
        assertThat(first.initiative()).containsExactlyElementsOf(joined);
        resolveWithWait(first);

        Round second = game.startRound(now);
        assertThat(second.initiative()).containsExactly(joined.get(1), joined.get(2), joined.get(3), joined.get(0));
        resolveWithWait(second);

        Round third = game.startRound(now);
        assertThat(third.initiative()).containsExactly(joined.get(2), joined.get(3), joined.get(0), joined.get(1));
        resolveWithWait(third);

        Round fourth = game.startRound(now);
        assertThat(fourth.initiative()).containsExactly(joined.get(3), joined.get(0), joined.get(1), joined.get(2));
    }

    @Test
    void resolvesEveryRegisterInInitiativeOrderAndIsDeterministic() {
        UUID leaderId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        VehicleState leader = state(leaderId, 3, 2);
        VehicleState follower = state(followerId, 1, 2);
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        programs.put(followerId, locked(followerId, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1));
        programs.put(leaderId, locked(leaderId, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1));
        List<UUID> initiative = List.of(leaderId, followerId);
        GameState initial = new GameState(new Board(10, 6), List.of(follower, leader));

        Round first = new Round(1, RoundPhase.PLANNING, programs, initiative, initial, List.of());
        Round second = new Round(1, RoundPhase.PLANNING, programs, initiative, initial, List.of());
        first.resolve(new MovementEngine());
        second.resolve(new MovementEngine());

        assertThat(first.playback()).extracting(RoundEvent::playerId)
                .containsExactly(leaderId, followerId, leaderId, followerId);
        assertThat(first.playback()).isEqualTo(second.playback());
        assertThat(first.finalVehicleStates()).isEqualTo(second.finalVehicleStates());
        assertThat(first.playback()).extracting(RoundEvent::sequence).containsExactly(1, 2, 3, 4);
    }

    @Test
    void roundResolutionDoesNotProduceAutomaticCannonOrBoardEffectEvents() {
        UUID playerId = UUID.randomUUID();
        VehicleState vehicle = state(playerId, 2, 2);
        Round round = new Round(1, Map.of(playerId, locked(playerId, MovementOrder.WAIT)),
                List.of(playerId), new GameState(
                        new Board(5, 5, java.util.Set.of(), java.util.Set.of(vehicle.position())),
                        List.of(vehicle)));

        round.resolve(new MovementEngine());

        assertThat(round.playback()).isEmpty();
    }

    @Test
    void crashedVehicleSkipsItsCurrentAndLaterRegisterCommands() {
        UUID pusherId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        VehicleState pusher = state(pusherId, 1, 2);
        VehicleState victim = state(victimId, 2, 2);
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        programs.put(pusherId, locked(pusherId, MovementOrder.FORWARD_1, MovementOrder.WAIT));
        programs.put(victimId, locked(victimId, MovementOrder.TURN_LEFT, MovementOrder.FORWARD_1));
        Round round = new Round(1, RoundPhase.PLANNING, programs, List.of(pusherId, victimId),
                new GameState(new Board(6, 6, java.util.Set.of(),
                        java.util.Set.of(new Position(3, 2))), List.of(pusher, victim)), List.of());

        round.resolve(new MovementEngine());

        assertThat(round.playback()).extracting(RoundEvent::type)
                .containsExactly(RoundEventType.CRASH, RoundEventType.RAM);
        assertThat(round.finalVehicleStates()).filteredOn(state -> state.vehicle().playerId().equals(victimId))
                .singleElement().satisfies(state -> {
                    assertThat(state.status()).isEqualTo(VehicleStatus.CRASHED);
                    assertThat(state.orientation()).isEqualTo(Direction.EAST);
                    assertThat(state.position()).isEqualTo(new Position(3, 2));
                });
    }

    @Test
    void assignsDistinctInitialVehiclePositions() {
        Game game = new Game(UUID.randomUUID(), new Board(5, 5));
        game.addPlayer("Alice", "a");
        game.addPlayer("Bob", "b");
        assertThat(game.getVehicleStates()).extracting(VehicleState::position).doesNotHaveDuplicates();
    }

    @Test
    void checkpointScoresOncePerPlayerAndScoreEventsRemainInCausalOrder() {
        UUID aliceId = UUID.randomUUID();
        Player alice = Player.create(aliceId, "Alice", "a");
        VehicleState vehicle = state(aliceId, 1, 2);
        Board board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(new Checkpoint("cp-1", new Position(2, 2))));
        Round round = new Round(1, Map.of(aliceId, locked(aliceId,
                MovementOrder.FORWARD_1, MovementOrder.REVERSE_1, MovementOrder.FORWARD_1)),
                List.of(aliceId), new GameState(board, List.of(vehicle)));

        round.resolve(new MovementEngine(), List.of(alice), GameConfiguration.defaults());

        assertThat(alice.getScore()).isEqualTo(2);
        assertThat(alice.getVisitedCheckpoints()).containsExactly("cp-1");
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.MOVE, RoundEventType.SCORE_CHANGED,
                RoundEventType.MOVE, RoundEventType.MOVE);
        assertThat(round.playback().get(1)).satisfies(event -> {
            assertThat(event.scoreReason()).isEqualTo(ScoreChangeReason.CHECKPOINT);
            assertThat(event.scoreDelta()).isEqualTo(2);
            assertThat(event.checkpointId()).isEqualTo("cp-1");
        });
    }

    @Test
    void differentPlayersCanScoreTheSameCheckpoint() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        Player alice = Player.create(aliceId, "Alice", "a");
        Player bob = Player.create(bobId, "Bob", "b");
        Board board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(new Checkpoint("cp-1", new Position(2, 2))));
        resolveSinglePlayer(board, alice, state(aliceId, 1, 2), MovementOrder.FORWARD_1);
        resolveSinglePlayer(board, bob, new VehicleState(new Vehicle(UUID.randomUUID(), bobId),
                new Position(2, 3), Direction.SOUTH, 0), MovementOrder.FORWARD_1);

        assertThat(alice.getScore()).isEqualTo(2);
        assertThat(bob.getScore()).isEqualTo(2);
    }

    @Test
    void forwardTwoAwardsPenaltiesAndOnePushScoreForEachOpponentCrashed() {
        UUID pusherId = UUID.randomUUID(); UUID middleId = UUID.randomUUID(); UUID frontId = UUID.randomUUID();
        Player pusher = Player.create(pusherId, "Pusher", "p");
        Player middle = Player.create(middleId, "Middle", "m");
        Player front = Player.create(frontId, "Front", "f");
        VehicleState pusherState = state(pusherId, 0, 1);
        VehicleState middleState = state(middleId, 1, 1);
        VehicleState frontState = state(frontId, 2, 1);
        Round round = new Round(1, Map.of(pusherId, locked(pusherId, MovementOrder.FORWARD_2)),
                List.of(pusherId), new GameState(new Board(3, 3), List.of(pusherState, middleState, frontState)));

        round.resolve(new MovementEngine(), List.of(pusher, middle, front), GameConfiguration.defaults());

        assertThat(pusher.getScore()).isEqualTo(2);
        assertThat(middle.getScore()).isEqualTo(-1);
        assertThat(front.getScore()).isEqualTo(-1);
        assertThat(round.playback()).filteredOn(event -> event.type() == RoundEventType.SCORE_CHANGED)
                .extracting(RoundEvent::scoreReason).containsExactly(
                        ScoreChangeReason.CRASH_PENALTY, ScoreChangeReason.PUSH_CRASH,
                        ScoreChangeReason.CRASH_PENALTY, ScoreChangeReason.PUSH_CRASH);
        assertThat(round.playback()).extracting(RoundEvent::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, round.playback().size()).boxed().toList());
    }

    @Test
    void pushedVehicleCanScoreACheckpoint() {
        UUID pusherId = UUID.randomUUID(); UUID pushedId = UUID.randomUUID();
        Player pusher = Player.create(pusherId, "Pusher", "p");
        Player pushed = Player.create(pushedId, "Pushed", "v");
        Board board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(new Checkpoint("cp", new Position(2, 1))));
        Round round = new Round(1, Map.of(pusherId, locked(pusherId, MovementOrder.FORWARD_1)),
                List.of(pusherId), new GameState(board, List.of(state(pusherId, 0, 1), state(pushedId, 1, 1))));

        round.resolve(new MovementEngine(), List.of(pusher, pushed), GameConfiguration.defaults());

        assertThat(pushed.getScore()).isEqualTo(2);
        assertThat(pusher.getScore()).isZero();
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.PUSH, RoundEventType.SCORE_CHANGED, RoundEventType.RAM);
    }

    @Test
    void ownCrashOnlyAppliesCrashPenalty() {
        UUID playerId = UUID.randomUUID();
        Player player = Player.create(playerId, "Alice", "a");
        Round round = new Round(1, Map.of(playerId, locked(playerId, MovementOrder.FORWARD_1)),
                List.of(playerId), new GameState(new Board(2, 2), List.of(state(playerId, 1, 1))));

        round.resolve(new MovementEngine(), List.of(player), GameConfiguration.defaults());

        assertThat(player.getScore()).isEqualTo(-1);
        assertThat(round.playback()).extracting(RoundEvent::type)
                .containsExactly(RoundEventType.CRASH, RoundEventType.SCORE_CHANGED);
        assertThat(round.playback().getLast().scoreReason()).isEqualTo(ScoreChangeReason.CRASH_PENALTY);
    }

    @Test
    void gameFinishesAtRoundLimitInsteadOfByDamage() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        GameConfiguration configuration = new GameConfiguration(2, 60, 1, 30, 1, 2, -1, 1);
        Game game = new Game(UUID.randomUUID(), List.of(), new Board(5, 5), GameStatus.RUNNING,
                Map.of(), null, configuration, now, now.plusSeconds(60));
        Player player = game.addPlayer("Alice", "a", now);
        Round round = game.startRound(now);
        round.lock(player.getId(), List.of(MovementOrder.WAIT));
        round.resolve(new MovementEngine(), game.getPlayers(), configuration);

        game.completeRound();

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThatThrownBy(() -> game.startRound(now.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The game is finished");
    }

    @Test
    void respawnsCrashedVehicleAtAssignedSpawnBeforePlanning() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        SpawnPoint assigned = new SpawnPoint(new Position(2, 1), Direction.WEST);
        Board board = new Board(4, 4, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                List.of(assigned, new SpawnPoint(new Position(3, 1), Direction.NORTH)));
        Player player = Player.create(UUID.randomUUID(), "Alice", "a");
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), player.getId(), assigned.position(), assigned.orientation());
        Game game = new Game(UUID.randomUUID(), List.of(player), board, GameStatus.RUNNING,
                Map.of(player.getId(), new VehicleState(vehicle, new Position(-1, 1), Direction.SOUTH, 2,
                        VehicleStatus.CRASHED)), null, new GameConfiguration(2, 60, 1, 30), now, now.plusSeconds(60));

        Round round = game.startRound(now);

        assertThat(round.initialState().vehicleStates()).singleElement().satisfies(state -> {
            assertThat(state.position()).isEqualTo(assigned.position());
            assertThat(state.orientation()).isEqualTo(Direction.WEST);
            assertThat(state.status()).isEqualTo(VehicleStatus.ACTIVE);
            assertThat(state.damage()).isZero();
        });
        assertThat(round.programs()).containsKey(player.getId());
        assertThat(round.startEvents()).singleElement()
                .extracting(RoundEvent::type).isEqualTo(RoundEventType.VEHICLE_RESPAWNED);
    }

    @Test
    void respawnUsesStableWrappedFallbackWhenAssignedSpawnIsOccupied() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        List<SpawnPoint> spawns = List.of(
                new SpawnPoint(new Position(0, 0), Direction.NORTH),
                new SpawnPoint(new Position(1, 0), Direction.EAST),
                new SpawnPoint(new Position(2, 0), Direction.SOUTH));
        Board board = new Board(3, 2, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), spawns);
        Player crashedPlayer = Player.create(UUID.randomUUID(), "Alice", "a");
        Player occupant = Player.create(UUID.randomUUID(), "Bob", "b");
        Vehicle crashedVehicle = new Vehicle(UUID.randomUUID(), crashedPlayer.getId(), spawns.getFirst().position(), Direction.WEST);
        Vehicle activeVehicle = new Vehicle(UUID.randomUUID(), occupant.getId(), spawns.getFirst().position(), Direction.NORTH);
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        states.put(crashedPlayer.getId(), new VehicleState(crashedVehicle, new Position(-1, 0), Direction.SOUTH, 0, VehicleStatus.CRASHED));
        states.put(occupant.getId(), new VehicleState(activeVehicle, spawns.getFirst().position(), Direction.NORTH));
        Game game = new Game(UUID.randomUUID(), List.of(crashedPlayer, occupant), board, GameStatus.RUNNING,
                states, null, new GameConfiguration(2, 60, 1, 30), now, now.plusSeconds(60));

        Round round = game.startRound(now);

        assertThat(round.initialState().vehicleStates()).filteredOn(s -> s.vehicle().playerId().equals(crashedPlayer.getId()))
                .singleElement().extracting(VehicleState::position).isEqualTo(spawns.get(1).position());
    }

    @Test
    void noFreeSpawnLeavesVehicleCrashedWithoutBlockingReadiness() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        List<SpawnPoint> spawns = List.of(new SpawnPoint(new Position(0, 0), Direction.NORTH),
                new SpawnPoint(new Position(1, 0), Direction.EAST));
        Board board = new Board(2, 2, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), spawns);
        Player waiting = Player.create(UUID.randomUUID(), "Waiting", "w");
        Player first = Player.create(UUID.randomUUID(), "First", "a");
        Player second = Player.create(UUID.randomUUID(), "Second", "b");
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        states.put(waiting.getId(), new VehicleState(new Vehicle(UUID.randomUUID(), waiting.getId(), spawns.getFirst().position(), Direction.SOUTH), new Position(-1, 0), Direction.SOUTH, 0, VehicleStatus.CRASHED));
        states.put(first.getId(), new VehicleState(new Vehicle(UUID.randomUUID(), first.getId(), spawns.getFirst().position(), Direction.NORTH), spawns.getFirst().position(), Direction.NORTH));
        states.put(second.getId(), new VehicleState(new Vehicle(UUID.randomUUID(), second.getId(), spawns.get(1).position(), Direction.EAST), spawns.get(1).position(), Direction.EAST));
        Game game = new Game(UUID.randomUUID(), List.of(waiting, first, second), board, GameStatus.RUNNING,
                states, null, new GameConfiguration(3, 60, 1, 30), now, now.plusSeconds(60));

        Round round = game.startRound(now);
        round.lock(first.getId(), List.of(MovementOrder.WAIT));
        round.lock(second.getId(), List.of(MovementOrder.WAIT));

        assertThat(round.programs()).doesNotContainKey(waiting.getId());
        assertThat(round.startEvents()).isEmpty();
        assertThat(game.getVehicleStates()).filteredOn(s -> s.vehicle().playerId().equals(waiting.getId()))
                .singleElement().extracting(VehicleState::status).isEqualTo(VehicleStatus.CRASHED);
        assertThat(round.allReady()).isTrue();
    }

    @Test
    void finishedPlacementsApplyAllTieBreakersAndShareCompleteTies() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        GameConfiguration configuration = new GameConfiguration(4, 60, 1, 30, 1, 2, -1, 1);
        Game game = new Game(UUID.randomUUID(), List.of(), new Board(5, 5), GameStatus.RUNNING,
                Map.of(), null, configuration, now, now.plusSeconds(60));
        Player first = game.addPlayer("First", "1", now);
        Player second = game.addPlayer("Second", "2", now);
        Player tied = game.addPlayer("Tied", "3", now);
        Player fourth = game.addPlayer("Fourth", "4", now);
        first.changeScore(10); first.visitCheckpoint("a"); first.visitCheckpoint("b"); first.recordCrash();
        second.changeScore(10); second.visitCheckpoint("a"); second.recordCrash();
        tied.changeScore(10); tied.visitCheckpoint("a"); tied.recordCrash();
        fourth.changeScore(9);
        Round round = game.startRound(now);
        resolveWithWait(round);
        game.completeRound();

        assertThat(game.getPlacements()).extracting(GamePlacement::placement).containsExactly(1, 2, 2, 4);
        assertThat(game.getPlacements()).extracting(GamePlacement::playerId)
                .containsExactly(first.getId(), second.getId(), tied.getId(), fourth.getId());
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner).singleElement()
                .extracting(GamePlacement::playerId).isEqualTo(first.getId());
    }

    private void resolveSinglePlayer(Board board, Player player, VehicleState state, MovementOrder order) {
        Round round = new Round(1, Map.of(player.getId(), locked(player.getId(), order)),
                List.of(player.getId()), new GameState(board, List.of(state)));
        round.resolve(new MovementEngine(), List.of(player), GameConfiguration.defaults());
    }

    private Game game(int programSize, Instant now) {
        return new Game(UUID.randomUUID(), List.of(), new Board(8, 8), GameStatus.RUNNING,
                Map.of(), null, new GameConfiguration(6, 60, programSize, 30),
                now, now.plusSeconds(60));
    }

    private void resolveWithWait(Round round) {
        round.initiative().forEach(playerId -> round.lock(playerId, List.of(MovementOrder.WAIT)));
        round.resolve(new MovementEngine());
    }

    private PlayerProgram locked(UUID playerId, MovementOrder... commands) {
        return new PlayerProgram(playerId, commands.length, List.of(commands), true);
    }

    private VehicleState state(UUID playerId, int x, int y) {
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId),
                new Position(x, y), Direction.EAST, 0);
    }
}
