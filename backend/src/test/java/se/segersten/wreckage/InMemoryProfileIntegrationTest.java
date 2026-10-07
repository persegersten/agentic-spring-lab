package se.segersten.wreckage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.ActionType;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameStatus;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.ScheduledAction;
import java.util.List;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.Wall;

@ActiveProfiles("in-memory")
@SpringBootTest
class InMemoryProfileIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

    @Test
    void persistsScoresCheckpointProgressDefinitionsAndScorePlayback() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var playerId = java.util.UUID.randomUUID();
        var player = Player.create(playerId, "Alice", "token");
        var vehicle = new Vehicle(java.util.UUID.randomUUID(), playerId);
        var state = new VehicleState(vehicle, new Position(1, 2), Direction.EAST);
        var checkpoint = new se.segersten.wreckage.game.domain.Checkpoint("CP1", 1, new Position(2, 2));
        var checkpoints = java.util.Set.of(checkpoint,
                new se.segersten.wreckage.game.domain.Checkpoint("CP2", 2, new Position(3, 2)),
                new se.segersten.wreckage.game.domain.Checkpoint("CP3", 3, new Position(3, 3)),
                new se.segersten.wreckage.game.domain.Checkpoint("CP4", 4, new Position(2, 3)));
        var base = new Board(5, 5);
        var board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(), checkpoints,
                base.spawnPoints(), java.util.List.of(), java.util.List.of(), java.util.Set.of(new Position(2, 2)));
        var program = new PlayerProgram(playerId, 1, java.util.List.of(MovementOrder.FORWARD_1), true);
        var round = new Round(1, RoundPhase.PLANNING, java.util.Map.of(playerId, program),
                java.util.List.of(playerId), new GameState(board, java.util.List.of(state)), java.util.List.of(),
                now.plusSeconds(30), java.util.Map.of(playerId, 0));
        round.resolve(new se.segersten.wreckage.game.engine.MovementEngine(), java.util.List.of(player),
                GameConfiguration.defaults());
        var game = new Game(java.util.UUID.randomUUID(), java.util.List.of(player), board, GameStatus.RUNNING,
                java.util.Map.of(playerId, state), round, GameConfiguration.defaults(), now, now.plusSeconds(60));

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getBoard().orderedCheckpoints()).containsExactlyElementsOf(board.orderedCheckpoints());
        assertThat(retrieved.getBoard().controlPoints()).containsExactly(new Position(2, 2));
        assertThat(retrieved.getConfiguration().controlPointScore()).isEqualTo(1);
        assertThat(retrieved.getPlayers().getFirst().getScore()).isEqualTo(3);
        assertThat(retrieved.getPlayers().getFirst().getVisitedCheckpoints()).containsExactly("CP1");
        assertThat(retrieved.getRound().initialScores()).containsEntry(playerId, 0);
        assertThat(retrieved.getRound().playback()).extracting(event -> event.type())
                .containsExactly(se.segersten.wreckage.game.domain.RoundEventType.MOVE,
                        se.segersten.wreckage.game.domain.RoundEventType.SCORE_CHANGED,
                        se.segersten.wreckage.game.domain.RoundEventType.SCORE_CHANGED);
        assertThat(retrieved.getRound().playback().get(1).newScore()).isEqualTo(2);
        assertThat(retrieved.getRound().playback().get(2).scoreReason())
                .isEqualTo(se.segersten.wreckage.game.domain.ScoreChangeReason.CONTROL_POINT);
        assertThat(retrieved.getRound().playback().get(2).newScore()).isEqualTo(3);
    }

    @Test
    void createsAndRetrievesGameUsingInMemoryDatabase() {
        Game created = gameService.createGame();

        Game retrieved = gameService.getGame(created.getId());

        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getBoard()).isEqualTo(created.getBoard());
        assertThat(retrieved.getPlayers()).isEmpty();
    }

    @Test
    void persistsResolvedProgramsAndPlaybackUsingInMemoryDatabase() {
        var hosted = gameService.createHostedGame(new GameConfiguration(2, 300, 5, 120));
        Game game = hosted.game();
        var alice = gameService.addPlayer(game.getId(), "Alice");
        var bob = gameService.addPlayer(game.getId(), "Bob");
        gameService.startGame(game.getId(), hosted.hostToken());

        Game aliceView = gameService.getPlayerGame(game.getId(), alice.player().getId(), alice.token());
        Game bobView = gameService.getPlayerGame(game.getId(), bob.player().getId(), bob.token());
        gameService.submitProgram(game.getId(), alice.player().getId(), alice.token(),
                java.util.Collections.nCopies(5, MovementOrder.WAIT));
        gameService.submitProgram(game.getId(), bob.player().getId(), bob.token(),
                java.util.Collections.nCopies(5, MovementOrder.WAIT));

        Game retrieved = gameService.getGame(game.getId());
        assertThat(retrieved.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(retrieved.getRound().allReady()).isTrue();
        assertThat(retrieved.getRound().playback()).allSatisfy(event -> {
            assertThat(event.sequence()).isPositive();
            assertThat(event.oldPosition()).isNotNull();
            assertThat(event.newPosition()).isNotNull();
        });
        assertThat(retrieved.getRound().programs().get(alice.player().getId()).commands()).hasSize(5);
        assertThat(retrieved.getRound().initiative()).containsExactly(
                alice.player().getId(), bob.player().getId());
        assertThat(retrieved.getVehicleStates()).hasSize(2);
        assertThat(retrieved.getRound().finalVehicleStates()).hasSize(2);
    }

    @Test
    void persistsPrivateScheduledActionUsingInMemoryDatabase() {
        var hosted = gameService.createHostedGame(new GameConfiguration(2, 300, 3, 120));
        var alice = gameService.addPlayer(hosted.game().getId(), "Alice");
        gameService.addPlayer(hosted.game().getId(), "Bob");
        gameService.startGame(hosted.game().getId(), hosted.hostToken());
        var action = new se.segersten.wreckage.game.domain.ScheduledAction(
                se.segersten.wreckage.game.domain.ActionType.SHIELD, 1);

        gameService.saveProgramDraft(hosted.game().getId(), alice.player().getId(), alice.token(),
                java.util.List.of(MovementOrder.WAIT), action);
        Game retrieved = gameService.getPlayerGame(hosted.game().getId(), alice.player().getId(), alice.token());

        assertThat(retrieved.getRound().programs().get(alice.player().getId()).scheduledAction()).isEqualTo(action);
    }

    @Test
    void persistsAuthoritativeSpecialAbilityPlaybackUsingInMemoryDatabase() {
        var hosted = gameService.createHostedGame(new GameConfiguration(2, 300, 1, 120));
        var alice = gameService.addPlayer(hosted.game().getId(), "Alice");
        var bob = gameService.addPlayer(hosted.game().getId(), "Bob");
        gameService.startGame(hosted.game().getId(), hosted.hostToken());
        var action = new ScheduledAction(ActionType.SHIELD, 1);

        gameService.submitProgram(hosted.game().getId(), alice.player().getId(), alice.token(),
                List.of(MovementOrder.WAIT), action);
        gameService.submitProgram(hosted.game().getId(), bob.player().getId(), bob.token(),
                List.of(MovementOrder.WAIT), null);
        Game retrieved = gameService.getGame(hosted.game().getId());

        assertThat(retrieved.getRound().playback())
                .filteredOn(event -> event.type() == RoundEventType.SHIELD_ACTIVATED)
                .singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.SHIELD_ACTIVATED);
            assertThat(event.actionType()).isEqualTo(ActionType.SHIELD);
            assertThat(event.playerId()).isEqualTo(alice.player().getId());
        });
    }

    @Test
    void persistsInitiativeAndSequentialMovementPlaybackUsingInMemoryDatabase() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var playerId = java.util.UUID.randomUUID();
        var vehicle = new Vehicle(java.util.UUID.randomUUID(), playerId);
        var state = new VehicleState(vehicle, new Position(2, 2), Direction.NORTH);
        var board = new Board(6, 6, java.util.Set.of(new Wall(new Position(3, 1), Direction.EAST)),
                java.util.Set.of(new Position(4, 5)));
        var program = new se.segersten.wreckage.game.domain.PlayerProgram(playerId, 1,
                java.util.List.of(se.segersten.wreckage.game.domain.MovementOrder.FORWARD_2), true);
        var round = new se.segersten.wreckage.game.domain.Round(1, java.util.Map.of(playerId, program), java.util.List.of(playerId),
                new se.segersten.wreckage.game.domain.GameState(board, java.util.List.of(state)));
        round.resolve(new se.segersten.wreckage.game.engine.MovementEngine());
        var game = new Game(java.util.UUID.randomUUID(),
                java.util.List.of(Player.create(playerId, "Alice", "token")),
                board, GameStatus.RUNNING, java.util.Map.of(playerId, state), round,
                GameConfiguration.defaults(), now, now.plusSeconds(60));

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getBoard().walls())
                .containsExactly(new Wall(new Position(3, 1), Direction.EAST));
        assertThat(retrieved.getBoard().pits()).containsExactly(new Position(4, 5));
        assertThat(retrieved.getVehicleStates()).extracting(VehicleState::status)
                .containsExactly(se.segersten.wreckage.game.domain.VehicleStatus.ACTIVE);
        assertThat(retrieved.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(retrieved.getRound().initiative()).containsExactly(playerId);
        assertThat(retrieved.getRound().playback()).extracting(event -> event.type())
                .containsExactly(se.segersten.wreckage.game.domain.RoundEventType.MOVE,
                        se.segersten.wreckage.game.domain.RoundEventType.MOVE);
    }

    @Test
    void persistsCrashedVehicleStatusAndCrashPlaybackUsingInMemoryDatabase() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var playerId = java.util.UUID.randomUUID();
        var vehicle = new Vehicle(java.util.UUID.randomUUID(), playerId);
        var active = new VehicleState(vehicle, new Position(2, 2), Direction.NORTH);
        var board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(new Position(2, 3)));
        var program = new PlayerProgram(playerId, 1, java.util.List.of(MovementOrder.FORWARD_1), true);
        var round = new Round(1, java.util.Map.of(playerId, program), java.util.List.of(playerId),
                new GameState(board, java.util.List.of(active)));
        round.resolve(new se.segersten.wreckage.game.engine.MovementEngine());
        VehicleState crashed = round.finalVehicleStates().getFirst();
        var game = new Game(java.util.UUID.randomUUID(),
                java.util.List.of(Player.create(playerId, "Alice", "token")), board, GameStatus.RUNNING,
                java.util.Map.of(playerId, crashed), round, GameConfiguration.defaults(), now,
                now.plusSeconds(60));

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getVehicleStates().getFirst().status())
                .isEqualTo(se.segersten.wreckage.game.domain.VehicleStatus.CRASHED);
        assertThat(retrieved.getRound().initialState().vehicleStates().getFirst().status())
                .isEqualTo(se.segersten.wreckage.game.domain.VehicleStatus.ACTIVE);
        assertThat(retrieved.getRound().playback()).extracting(event -> event.type())
                .containsExactly(se.segersten.wreckage.game.domain.RoundEventType.CRASH);
    }

    @Test
    void persistsSpawnAssignmentCrashCountAndRoundStartRespawnEvent() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var player = Player.create(java.util.UUID.randomUUID(), "Alice", "token");
        player.recordCrash();
        var spawn = new se.segersten.wreckage.game.domain.SpawnPoint(new Position(3, 2), Direction.WEST);
        var board = new Board(5, 5, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.List.of(spawn));
        var vehicle = new Vehicle(java.util.UUID.randomUUID(), player.getId(), spawn.position(), spawn.orientation());
        var crashed = new VehicleState(vehicle, new Position(-1, 2), Direction.NORTH, se.segersten.wreckage.game.domain.VehicleStatus.CRASHED);
        var game = new Game(java.util.UUID.randomUUID(), java.util.List.of(player), board, GameStatus.RUNNING,
                java.util.Map.of(player.getId(), crashed), null, new GameConfiguration(2, 60, 1, 30), now,
                now.plusSeconds(60));
        game.startRound(now);

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getPlayers().getFirst().getCrashes()).isEqualTo(1);
        assertThat(retrieved.getVehicleStates().getFirst().vehicle().spawnPoint()).isEqualTo(spawn.position());
        assertThat(retrieved.getVehicleStates().getFirst().vehicle().spawnOrientation()).isEqualTo(Direction.WEST);
        assertThat(retrieved.getRound().startEvents()).singleElement()
                .extracting(event -> event.type())
                .isEqualTo(se.segersten.wreckage.game.domain.RoundEventType.VEHICLE_RESPAWNED);
    }

    @Test
    void persistsProgramDraftUsingInMemoryDatabase() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var playerId = java.util.UUID.randomUUID();
        var vehicle = new Vehicle(java.util.UUID.randomUUID(), playerId);
        var state = new VehicleState(vehicle, new Position(2, 2), Direction.NORTH);
        var board = new Board(5, 5);
        var program = new PlayerProgram(playerId, 2,
                java.util.List.of(MovementOrder.WAIT, MovementOrder.FORWARD_1), false);
        var round = new Round(2, java.util.Map.of(playerId, program), java.util.List.of(playerId),
                new GameState(board, java.util.List.of(state)));
        var game = new Game(java.util.UUID.randomUUID(),
                java.util.List.of(Player.create(playerId, "Per", "token")),
                board, GameStatus.RUNNING, java.util.Map.of(playerId, state), round,
                GameConfiguration.defaults(), now, now.plusSeconds(60));

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getRound().programs().get(playerId).commands()).containsExactly(
                MovementOrder.WAIT, MovementOrder.FORWARD_1);
    }

    @Test
    void persistsRocketAmmoDamageConfigurationAndPlaybackUsingInMemoryDatabase() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var shooter = Player.create(java.util.UUID.randomUUID(), "Shooter", "token-a");
        var target = Player.create(java.util.UUID.randomUUID(), "Target", "token-b");
        var shooterState = new VehicleState(new Vehicle(java.util.UUID.randomUUID(), shooter.getId(),
                new Position(0, 0), Direction.SOUTH,
                se.segersten.wreckage.game.domain.PrimaryWeapon.ROCKET,
                se.segersten.wreckage.game.domain.SpecialAbility.SIDE_STEP),
                new Position(0, 1), Direction.EAST);
        var targetState = new VehicleState(new Vehicle(java.util.UUID.randomUUID(), target.getId()),
                new Position(2, 1), Direction.NORTH, se.segersten.wreckage.game.domain.VehicleStatus.ACTIVE, 0);
        var programs = java.util.Map.of(
                shooter.getId(), new PlayerProgram(shooter.getId(), 1, java.util.List.of(MovementOrder.WAIT), true,
                        new se.segersten.wreckage.game.domain.ScheduledAction(
                                se.segersten.wreckage.game.domain.ActionType.ROCKET, 1)),
                target.getId(), new PlayerProgram(target.getId(), 1, java.util.List.of(MovementOrder.WAIT), true));
        var board = new Board(5, 5);
        var round = new Round(1, programs, java.util.List.of(shooter.getId(), target.getId()),
                new GameState(board, java.util.List.of(shooterState, targetState)));
        round.resolve(new se.segersten.wreckage.game.engine.MovementEngine(), java.util.List.of(shooter, target),
                GameConfiguration.defaults());
        var game = new Game(java.util.UUID.randomUUID(), java.util.List.of(shooter, target), board,
                GameStatus.RUNNING, java.util.Map.of(shooter.getId(), shooterState, target.getId(), targetState),
                round, GameConfiguration.defaults(), now, now.plusSeconds(60));
        game.completeRound();

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getConfiguration().weaponCrashScore()).isEqualTo(1);
        assertThat(retrieved.getRound().initialState().vehicleStates()).extracting(VehicleState::damage)
                .containsExactly(0, 0);
        assertThat(retrieved.getRound().playback()).extracting(event -> event.type())
                .containsExactly(se.segersten.wreckage.game.domain.RoundEventType.WEAPON_FIRED,
                        se.segersten.wreckage.game.domain.RoundEventType.AMMO_CHANGED,
                        se.segersten.wreckage.game.domain.RoundEventType.WEAPON_HIT,
                        se.segersten.wreckage.game.domain.RoundEventType.DAMAGE_APPLIED);
        assertThat(retrieved.getRound().playback().getLast().newDamage()).isEqualTo(2);
        assertThat(retrieved.getRound().finalVehicleStates()).extracting(VehicleState::damage)
                .containsExactly(0, 2);
        assertThat(retrieved.getRound().finalVehicleStates()).extracting(VehicleState::rocketAmmo)
                .containsExactlyInAnyOrder(0, 1);
        assertThat(retrieved.getRound().playback().get(1).actionType())
                .isEqualTo(se.segersten.wreckage.game.domain.ActionType.ROCKET);
        assertThat(retrieved.getVehicleStates()).extracting(VehicleState::rocketAmmo).containsExactlyInAnyOrder(0, 1);
        assertThat(retrieved.getVehicleStates()).filteredOn(state -> state.vehicle().playerId().equals(shooter.getId()))
                .singleElement().satisfies(state -> {
                    assertThat(state.vehicle().primaryWeapon()).isEqualTo(se.segersten.wreckage.game.domain.PrimaryWeapon.ROCKET);
                    assertThat(state.vehicle().specialAbility()).isEqualTo(se.segersten.wreckage.game.domain.SpecialAbility.SIDE_STEP);
                });
    }
}
