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
    void rejectsStartingRoundWithoutADealtHand() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(now);
        game.addPlayer("Alice", "a");

        assertThatThrownBy(() -> game.startRound(now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A dealt hand must contain exactly 8 programming cards");
    }

    @Test
    void startsEmptyProgramsAndResolvesCompleteOnes() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(now);
        Player player = game.addPlayer("Alice", "a");

        Round round = game.startRound(now, handsFor(game));
        assertThat(round.programs().get(player.getId()).commands()).isEmpty();
        assertThat(round.initiative()).containsExactly(player.getId());

        round.lock(player.getId(), List.of(
                MovementOrder.FORWARD_1, MovementOrder.TURN_RIGHT, MovementOrder.FORWARD_2,
                MovementOrder.TURN_LEFT, MovementOrder.REVERSE_1));
        round.resolve(new MovementEngine());

        assertThat(round.phase()).isEqualTo(RoundPhase.PLAYBACK);
    }

    @Test
    void rotatesInitiativeLeftExactlyOnceWhenEachNewRoundStarts() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(now);
        List<UUID> joined = List.of(
                game.addPlayer("A", "a").getId(),
                game.addPlayer("B", "b").getId(),
                game.addPlayer("C", "c").getId(),
                game.addPlayer("D", "d").getId());

        Round first = game.startRound(now, handsFor(game));
        assertThat(first.initiative()).containsExactlyElementsOf(joined);
        resolveWithWait(first);

        Round second = game.startRound(now, handsFor(game));
        assertThat(second.initiative()).containsExactly(joined.get(1), joined.get(2), joined.get(3), joined.get(0));
        resolveWithWait(second);

        Round third = game.startRound(now, handsFor(game));
        assertThat(third.initiative()).containsExactly(joined.get(2), joined.get(3), joined.get(0), joined.get(1));
        resolveWithWait(third);

        Round fourth = game.startRound(now, handsFor(game));
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
        assertThat(first.playback()).extracting(RoundEvent::registerIndex).containsExactly(1, 1, 2, 2);
    }

    @Test
    void boardEffectsOccurBetweenRegistersAndChangeNextRegisterState() {
        UUID playerId = UUID.randomUUID();
        VehicleState vehicle=state(playerId,0,2);Board base=new Board(5,5);Board board=new Board(5,5,java.util.Set.of(),java.util.Set.of(),java.util.Set.of(),base.spawnPoints(),List.of(new Conveyor(new Position(1,2),Direction.EAST)),List.of(new Rotator(new Position(2,2),Rotation.CLOCKWISE)));Round round=new Round(1,Map.of(playerId,locked(playerId,MovementOrder.FORWARD_1,MovementOrder.FORWARD_1)),List.of(playerId),new GameState(board,List.of(vehicle)));

        round.resolve(new MovementEngine());

        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(RoundEventType.MOVE,RoundEventType.CONVEYOR_MOVE,RoundEventType.ROTATOR_TURN,RoundEventType.MOVE);assertThat(round.playback().getLast().newPosition()).isEqualTo(new Position(2,1));
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
                checkpoints(new Position(2, 2), new Position(4, 4), new Position(4, 3), new Position(3, 4)));
        Round round = new Round(1, Map.of(aliceId, locked(aliceId,
                MovementOrder.FORWARD_1, MovementOrder.REVERSE_1, MovementOrder.FORWARD_1)),
                List.of(aliceId), new GameState(board, List.of(vehicle)));

        round.resolve(new MovementEngine(), List.of(alice), GameConfiguration.defaults());

        assertThat(alice.getScore()).isEqualTo(2);
        assertThat(alice.getVisitedCheckpoints()).containsExactly("CP1");
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.MOVE, RoundEventType.SCORE_CHANGED,
                RoundEventType.MOVE, RoundEventType.MOVE);
        assertThat(round.playback().get(1)).satisfies(event -> {
            assertThat(event.scoreReason()).isEqualTo(ScoreChangeReason.CHECKPOINT);
            assertThat(event.scoreDelta()).isEqualTo(2);
            assertThat(event.checkpointId()).isEqualTo("CP1");
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
                new Position(2, 3), Direction.SOUTH), MovementOrder.FORWARD_1);

        assertThat(alice.getScore()).isEqualTo(2);
        assertThat(bob.getScore()).isEqualTo(2);
    }

    @Test
    void controlPointScoresExactlyOnceAtRoundEndWithConfiguredValue() {
        UUID playerId = UUID.randomUUID();
        Player player = Player.create(playerId, "Alice", "a");
        Board base = new Board(5, 5);
        Board board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                base.spawnPoints(), List.of(), List.of(), java.util.Set.of(new Position(2, 2)));
        Round round = new Round(1, Map.of(playerId, locked(playerId, MovementOrder.WAIT, MovementOrder.WAIT)),
                List.of(playerId), new GameState(board, List.of(state(playerId, 2, 2))));
        GameConfiguration configuration = new GameConfiguration(2, 60, 5, 30, 6, 2, 3, -1, 1);

        round.resolve(new MovementEngine(), List.of(player), configuration);

        assertThat(GameConfiguration.defaults().controlPointScore()).isEqualTo(1);
        assertThat(player.getScore()).isEqualTo(3);
        assertThat(round.playback()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.SCORE_CHANGED);
            assertThat(event.scoreReason()).isEqualTo(ScoreChangeReason.CONTROL_POINT);
            assertThat(event.scoreDelta()).isEqualTo(3);
            assertThat(event.newPosition()).isEqualTo(new Position(2, 2));
        });
    }

    @Test
    void finalConveyorPositionDeterminesControlPointScoring() {
        UUID movedOnId = UUID.randomUUID();
        UUID movedOffId = UUID.randomUUID();
        Player movedOn = Player.create(movedOnId, "On", "a");
        Player movedOff = Player.create(movedOffId, "Off", "b");
        Board base = new Board(4, 3);
        Board board = new Board(4, 3, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                base.spawnPoints(), List.of(
                        new Conveyor(new Position(0, 1), Direction.EAST),
                        new Conveyor(new Position(2, 1), Direction.EAST)), List.of(),
                java.util.Set.of(new Position(1, 1), new Position(2, 1)));
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        programs.put(movedOnId, locked(movedOnId, MovementOrder.WAIT));
        programs.put(movedOffId, locked(movedOffId, MovementOrder.WAIT));
        Round round = new Round(1, programs, List.of(movedOnId, movedOffId), new GameState(board,
                List.of(state(movedOnId, 0, 1), state(movedOffId, 2, 1))));

        round.resolve(new MovementEngine(), List.of(movedOn, movedOff), GameConfiguration.defaults());

        assertThat(movedOn.getScore()).isEqualTo(1);
        assertThat(movedOff.getScore()).isZero();
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.CONVEYOR_MOVE, RoundEventType.CONVEYOR_MOVE, RoundEventType.SCORE_CHANGED);
        assertThat(round.playback().getLast().playerId()).isEqualTo(movedOnId);
        assertThat(round.playback().getLast().newPosition()).isEqualTo(new Position(1, 1));
    }

    @Test
    void crashedVehiclesAndBoardsWithoutControlPointsReceiveNoControlPointScore() {
        UUID crashedId = UUID.randomUUID();
        Player crashed = Player.create(crashedId, "Crashed", "a");
        Board base = new Board(3, 3);
        Board controlBoard = new Board(3, 3, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                base.spawnPoints(), List.of(), List.of(), java.util.Set.of(new Position(1, 1)));
        VehicleState crashedState = new VehicleState(new Vehicle(UUID.randomUUID(), crashedId),
                new Position(1, 1), Direction.EAST, VehicleStatus.CRASHED);
        Round crashedRound = new Round(1, Map.of(crashedId, locked(crashedId, MovementOrder.WAIT)),
                List.of(crashedId), new GameState(controlBoard, List.of(crashedState)));
        crashedRound.resolve(new MovementEngine(), List.of(crashed), GameConfiguration.defaults());

        UUID activeId = UUID.randomUUID();
        Player active = Player.create(activeId, "Active", "b");
        Round emptyBoardRound = new Round(1, Map.of(activeId, locked(activeId, MovementOrder.WAIT)),
                List.of(activeId), new GameState(new Board(3, 3), List.of(state(activeId, 1, 1))));
        emptyBoardRound.resolve(new MovementEngine(), List.of(active), GameConfiguration.defaults());

        assertThat(crashed.getScore()).isZero();
        assertThat(crashedRound.playback()).isEmpty();
        assertThat(active.getScore()).isZero();
        assertThat(emptyBoardRound.playback()).isEmpty();
    }

    @Test
    void boundaryBlockedRammingAwardsNoDamageOrPoints() {
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

        assertThat(pusher.getScore()).isZero();
        assertThat(middle.getScore()).isZero();
        assertThat(front.getScore()).isZero();
        assertThat(round.playback()).isEmpty();
        assertThat(round.finalVehicleStates()).extracting(VehicleState::position)
                .containsExactly(new Position(0, 1), new Position(1, 1), new Position(2, 1));
    }

    @Test
    void pushedVehicleCanScoreACheckpoint() {
        UUID pusherId = UUID.randomUUID(); UUID pushedId = UUID.randomUUID();
        Player pusher = Player.create(pusherId, "Pusher", "p");
        Player pushed = Player.create(pushedId, "Pushed", "v");
        Board board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(2, 1), new Position(4, 4), new Position(4, 3), new Position(3, 4)));
        Round round = new Round(1, Map.of(pusherId, locked(pusherId, MovementOrder.FORWARD_1)),
                List.of(pusherId), new GameState(board, List.of(state(pusherId, 0, 1), state(pushedId, 1, 1))));

        round.resolve(new MovementEngine(), List.of(pusher, pushed), GameConfiguration.defaults());

        assertThat(pushed.getScore()).isEqualTo(2);
        assertThat(pusher.getScore()).isZero();
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.PUSH, RoundEventType.SCORE_CHANGED, RoundEventType.RAM);
    }

    @Test
    void laserDisplacementCapturesCheckpointWithoutAwardingTheShooterCombatPoints() {
        UUID shooterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Player shooter = Player.create(shooterId, "Shooter", "s");
        Player target = Player.create(targetId, "Target", "t");
        Board board = new Board(7, 3, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(3, 1), new Position(4, 2), new Position(5, 2), new Position(6, 2)));
        Round round = new Round(1, Map.of(
                shooterId, locked(shooterId, MovementOrder.LASER),
                targetId, locked(targetId, MovementOrder.WAIT)),
                List.of(shooterId, targetId), new GameState(board,
                        List.of(state(shooterId, 0, 1), state(targetId, 2, 1))));

        round.resolve(new MovementEngine(), List.of(shooter, target), GameConfiguration.defaults());

        assertThat(target.getVisitedCheckpoints()).containsExactly("CP1");
        assertThat(target.getScore()).isEqualTo(GameConfiguration.DEFAULT_CHECKPOINT_SCORE);
        assertThat(shooter.getScore()).isZero();
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT,
                RoundEventType.PUSH, RoundEventType.SCORE_CHANGED, RoundEventType.PUSH);
    }

    @Test
    void fourthCheckpointCapturedByLaserPushStopsAtTheWinningDisplacement() {
        UUID shooterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Player shooter = Player.create(shooterId, "Shooter", "s");
        Player target = Player.create(targetId, "Target", "t");
        target.visitCheckpoint("CP1");
        target.visitCheckpoint("CP2");
        target.visitCheckpoint("CP3");
        Board board = new Board(7, 3, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(0, 2), new Position(1, 2), new Position(2, 2), new Position(3, 1)));
        Round round = new Round(1, Map.of(
                shooterId, locked(shooterId, MovementOrder.LASER),
                targetId, locked(targetId, MovementOrder.WAIT)),
                List.of(shooterId, targetId), new GameState(board,
                        List.of(state(shooterId, 0, 1), state(targetId, 2, 1))));

        round.resolve(new MovementEngine(), List.of(shooter, target), GameConfiguration.defaults());

        assertThat(target.hasCompletedCheckpoints(board)).isTrue();
        assertThat(round.finalVehicleStates()).filteredOn(v -> v.vehicle().playerId().equals(targetId))
                .singleElement().extracting(VehicleState::position).isEqualTo(new Position(3, 1));
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT,
                RoundEventType.PUSH, RoundEventType.SCORE_CHANGED);
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
    void gameFinishesAtRoundLimit() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        GameConfiguration configuration = new GameConfiguration(2, 60, 5, 30, 1, 2, -1, 1);
        Game game = new Game(UUID.randomUUID(), List.of(), new Board(5, 5), GameStatus.RUNNING,
                Map.of(), null, configuration, now, now.plusSeconds(60));
        Player player = game.addPlayer("Alice", "a", now);
        Round round = game.startRound(now, handsFor(game));
        lockFirstFive(round);
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
                Map.of(player.getId(), new VehicleState(vehicle, new Position(-1, 1), Direction.SOUTH, VehicleStatus.CRASHED)), null, new GameConfiguration(2, 60, 5, 30), now, now.plusSeconds(60));

        Round round = game.startRound(now, handsFor(game));

        assertThat(round.initialState().vehicleStates()).singleElement().satisfies(state -> {
            assertThat(state.position()).isEqualTo(assigned.position());
            assertThat(state.orientation()).isEqualTo(Direction.WEST);
            assertThat(state.status()).isEqualTo(VehicleStatus.ACTIVE);
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
        states.put(crashedPlayer.getId(), new VehicleState(crashedVehicle, new Position(-1, 0), Direction.SOUTH, VehicleStatus.CRASHED));
        states.put(occupant.getId(), new VehicleState(activeVehicle, spawns.getFirst().position(), Direction.NORTH));
        Game game = new Game(UUID.randomUUID(), List.of(crashedPlayer, occupant), board, GameStatus.RUNNING,
                states, null, new GameConfiguration(2, 60, 5, 30), now, now.plusSeconds(60));

        Round round = game.startRound(now, handsFor(game));

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
        states.put(waiting.getId(), new VehicleState(new Vehicle(UUID.randomUUID(), waiting.getId(), spawns.getFirst().position(), Direction.SOUTH), new Position(-1, 0), Direction.SOUTH, VehicleStatus.CRASHED));
        states.put(first.getId(), new VehicleState(new Vehicle(UUID.randomUUID(), first.getId(), spawns.getFirst().position(), Direction.NORTH), spawns.getFirst().position(), Direction.NORTH));
        states.put(second.getId(), new VehicleState(new Vehicle(UUID.randomUUID(), second.getId(), spawns.get(1).position(), Direction.EAST), spawns.get(1).position(), Direction.EAST));
        Game game = new Game(UUID.randomUUID(), List.of(waiting, first, second), board, GameStatus.RUNNING,
                states, null, new GameConfiguration(3, 60, 5, 30), now, now.plusSeconds(60));

        Round round = game.startRound(now, handsFor(game));
        lockFirstFive(round);

        assertThat(round.programs()).doesNotContainKey(waiting.getId());
        assertThat(round.startEvents()).isEmpty();
        assertThat(game.getVehicleStates()).filteredOn(s -> s.vehicle().playerId().equals(waiting.getId()))
                .singleElement().extracting(VehicleState::status).isEqualTo(VehicleStatus.CRASHED);
        assertThat(round.allReady()).isTrue();
    }

    @Test
    void finishedPlacementsUseCheckpointProgressThenDistanceAndAllowDraws() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        GameConfiguration configuration = new GameConfiguration(4, 60, 5, 30, 1, 2, -1, 1);
        Board board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(1, 1), new Position(3, 3), new Position(4, 3), new Position(4, 4)));
        Player first = Player.create(UUID.randomUUID(), "First", "1");
        Player tied = Player.create(UUID.randomUUID(), "Tied", "2");
        Player farther = Player.create(UUID.randomUUID(), "Farther", "3");
        Player behind = Player.create(UUID.randomUUID(), "Behind", "4");
        first.visitCheckpoint("CP1"); tied.visitCheckpoint("CP1"); farther.visitCheckpoint("CP1");
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        states.put(first.getId(), state(first.getId(), 2, 3));
        states.put(tied.getId(), state(tied.getId(), 3, 2));
        states.put(farther.getId(), state(farther.getId(), 0, 0));
        states.put(behind.getId(), state(behind.getId(), 1, 1));
        Game game = new Game(UUID.randomUUID(), List.of(first, tied, farther, behind), board,
                GameStatus.FINISHED, states, null, configuration, now, now.plusSeconds(60));

        assertThat(game.getPlacements()).extracting(GamePlacement::placement).containsExactly(1, 1, 3, 4);
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner)
                .extracting(GamePlacement::playerId).containsExactly(first.getId(), tied.getId());
        assertThat(game.getPlacements()).extracting(GamePlacement::distanceToNextCheckpoint)
                .containsExactly(1, 1, 6, 0);
    }

    @Test
    void allZeroScoresProduceJointWinners() {
        Player alice = Player.create(UUID.randomUUID(), "Alice", "a");
        Player bob = Player.create(UUID.randomUUID(), "Bob", "b");
        Game game = new Game(UUID.randomUUID(), List.of(alice, bob), new Board(10, 10), GameStatus.FINISHED);

        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner)
                .extracting(GamePlacement::playerId).containsExactly(alice.getId(), bob.getId());
    }

    @Test
    void survivorCountDoesNotFinishBeforeRoundLimit() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Player active = Player.create(UUID.randomUUID(), "Active", "a");
        Player crashed = Player.create(UUID.randomUUID(), "Crashed", "b");
        Vehicle activeVehicle = new Vehicle(UUID.randomUUID(), active.getId());
        Vehicle crashedVehicle = new Vehicle(UUID.randomUUID(), crashed.getId());
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        states.put(active.getId(), new VehicleState(activeVehicle, new Position(0, 0), Direction.SOUTH));
        states.put(crashed.getId(), new VehicleState(crashedVehicle, new Position(-1, 0), Direction.SOUTH,
                VehicleStatus.CRASHED));
        Board board = new Board(10, 10, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                List.of(new SpawnPoint(new Position(0, 0), Direction.SOUTH)));
        Game game = new Game(UUID.randomUUID(), List.of(active, crashed), board, GameStatus.RUNNING,
                states, null, new GameConfiguration(2, 60, 5, 30, 7, 2, -1, 1), now,
                now.plusSeconds(60));

        Round round = game.startRound(now, handsFor(game));
        lockFirstFive(round);
        round.resolve(new MovementEngine(), game.getPlayers(), game.getConfiguration());
        game.completeRound();

        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
    }

    private Round resolveSinglePlayer(Board board, Player player, VehicleState state, MovementOrder order) {
        Round round = new Round(1, Map.of(player.getId(), locked(player.getId(), order)),
                List.of(player.getId()), new GameState(board, List.of(state)));
        round.resolve(new MovementEngine(), List.of(player), GameConfiguration.defaults());
        return round;
    }

    @Test
    void checkpointsCaptureOnlyInMapOrderIncludingIntermediateMovementTiles() {
        UUID playerId = UUID.randomUUID();
        Player player = Player.create(playerId, "Alice", "a");
        Board board = new Board(7, 3, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(1, 1), new Position(3, 1), new Position(4, 1), new Position(5, 1)));

        resolveSinglePlayer(board, player, state(playerId, 2, 1), MovementOrder.FORWARD_1);
        assertThat(player.getCapturedCheckpointCount(board)).isZero();
        assertThat(player.getNextCheckpoint(board).id()).isEqualTo("CP1");

        resolveSinglePlayer(board, player, state(playerId, 0, 1), MovementOrder.FORWARD_2);
        assertThat(player.getCapturedCheckpointCount(board)).isOne();
        assertThat(player.getNextCheckpoint(board).id()).isEqualTo("CP2");

        resolveSinglePlayer(board, player, state(playerId, 0, 1), MovementOrder.FORWARD_1);
        assertThat(player.getCapturedCheckpointCount(board)).isOne();
    }

    @Test
    void blockedMovementDoesNotCaptureCheckpoint() {
        UUID playerId = UUID.randomUUID();
        Player player = Player.create(playerId, "Alice", "a");
        Board base = new Board(6, 3);
        Board board = new Board(6, 3, java.util.Set.of(new Wall(new Position(0, 1), Direction.EAST)),
                java.util.Set.of(), checkpoints(new Position(1, 1), new Position(2, 1),
                        new Position(3, 1), new Position(4, 1)), base.spawnPoints());

        Round round = resolveSinglePlayer(board, player, state(playerId, 0, 1), MovementOrder.FORWARD_1);

        assertThat(player.getCapturedCheckpointCount(board)).isZero();
        assertThat(round.playback()).isEmpty();
    }

    @Test
    void checkpointProgressSurvivesAcrossRounds() {
        UUID playerId = UUID.randomUUID();
        Player player = Player.create(playerId, "Alice", "a");
        Board board = new Board(6, 3, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(1, 1), new Position(2, 1), new Position(3, 1), new Position(4, 1)));

        resolveSinglePlayer(board, player, state(playerId, 0, 1), MovementOrder.FORWARD_1);
        resolveSinglePlayer(board, player, state(playerId, 1, 1), MovementOrder.FORWARD_1);

        assertThat(player.getCapturedCheckpointCount(board)).isEqualTo(2);
        assertThat(player.getNextCheckpoint(board).id()).isEqualTo("CP3");
    }

    @Test
    void fourthCheckpointEndsMatchAndStopsLaterPlayerMovement() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Board board = new Board(7, 3, java.util.Set.of(), java.util.Set.of(),
                checkpoints(new Position(1, 1), new Position(2, 1), new Position(3, 1), new Position(4, 1)));
        Player winner = Player.create(UUID.randomUUID(), "Winner", "w");
        Player opponent = Player.create(UUID.randomUUID(), "Opponent", "o");
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        states.put(winner.getId(), state(winner.getId(), 0, 1));
        states.put(opponent.getId(), state(opponent.getId(), 0, 2));
        Game game = new Game(UUID.randomUUID(), List.of(winner, opponent), board, GameStatus.RUNNING,
                states, null, new GameConfiguration(2, 60, 5, 30, 7, 2, -1, 1), now, now.plusSeconds(60));
        Round round = game.startRound(now, handsFor(game));
        round.lock(winner.getId(), List.of(MovementOrder.FORWARD_2, MovementOrder.FORWARD_2,
                MovementOrder.FORWARD_1, MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT));
        round.lock(opponent.getId(), List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_2, MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT));

        round.resolve(new MovementEngine(), game.getPlayers(), game.getConfiguration());
        game.completeRound();

        assertThat(winner.getCapturedCheckpointCount(board)).isEqualTo(4);
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner)
                .extracting(GamePlacement::playerId).containsExactly(winner.getId());
        assertThat(round.playback()).filteredOn(event -> event.playerId().equals(opponent.getId())
                && event.type() == RoundEventType.MOVE).hasSize(1);
    }

    private Game game(Instant now) {
        return new Game(UUID.randomUUID(), List.of(), new Board(8, 8), GameStatus.RUNNING,
                Map.of(), null, new GameConfiguration(6, 60, 5, 30),
                now, now.plusSeconds(60));
    }

    private void resolveWithWait(Round round) {
        lockFirstFive(round);
        round.resolve(new MovementEngine());
    }

    private PlayerProgram locked(UUID playerId, MovementOrder... commands) {
        return new PlayerProgram(playerId, 5, five(commands), true);
    }

    private List<MovementOrder> five(MovementOrder... commands) {
        var result = new java.util.ArrayList<>(List.of(commands));
        while (result.size() < 5) result.add(MovementOrder.WAIT);
        return List.copyOf(result);
    }

    private Map<UUID, List<MovementOrder>> handsFor(Game game) {
        return game.getPlayers().stream().collect(java.util.stream.Collectors.toMap(Player::getId,
                ignored -> List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                        MovementOrder.FORWARD_2, MovementOrder.FORWARD_2, MovementOrder.REVERSE_1,
                        MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT, MovementOrder.U_TURN)));
    }

    private void lockFirstFive(Round round) {
        round.initiative().forEach(playerId -> round.lock(playerId,
                round.programs().get(playerId).hand().subList(0, 5)));
    }

    private VehicleState state(UUID playerId, int x, int y) {
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId),
                new Position(x, y), Direction.EAST);
    }

    private java.util.Set<Checkpoint> checkpoints(Position first, Position second, Position third, Position fourth) {
        return java.util.Set.of(new Checkpoint("CP1", 1, first), new Checkpoint("CP2", 2, second),
                new Checkpoint("CP3", 3, third), new Checkpoint("CP4", 4, fourth));
    }
}
