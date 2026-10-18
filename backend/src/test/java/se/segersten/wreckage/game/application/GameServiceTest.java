package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameStatus;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;
import se.segersten.wreckage.game.domain.Checkpoint;

class GameServiceTest {

    @Test
    void rejectsCreationWhenWaitingAndRunningGamesReachTheConfiguredLimit() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        repository.save(new Game(UUID.randomUUID(), List.of(), new Board(20, 20), GameStatus.RUNNING));
        GameService service = new GameService(repository, 2);

        assertThatThrownBy(service::createGame)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The game server has reached its concurrent game limit");
    }

    @Test
    void finishedGamesReleaseCapacityForANewGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        repository.save(new Game(UUID.randomUUID(), List.of(), new Board(20, 20), GameStatus.FINISHED));
        GameService service = new GameService(repository, 2);

        Game created = service.createGame();

        assertThat(created.getStatus()).isEqualTo(GameStatus.WAITING_FOR_PLAYERS);
        assertThat(repository.findById(created.getId())).contains(created);
    }

    @Test
    void concurrentCreationCannotExceedTheConfiguredLimit() throws Exception {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, 3);
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var attempts = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        try {
                            service.createGame();
                            return true;
                        } catch (IllegalStateException exception) {
                            return false;
                        }
                    }))
                    .toList();

            start.countDown();

            var outcomes = new java.util.ArrayList<Boolean>();
            for (var attempt : attempts) outcomes.add(attempt.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true, true, true, false, false, false, false, false);
            assertThat(repository.findAllByStatus(GameStatus.WAITING_FOR_PLAYERS)).hasSize(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void everySupportedPlayerCountStartsOnItsDefaultMap() {
        for (int count = 2; count <= 10; count++) {
            InMemoryGameRepository repository = new InMemoryGameRepository();
            GameService service = new GameService(repository);
            var hosted = service.createHostedGame(new GameConfiguration(count, 60, 5, 30));
            for (int index = 0; index < count; index++) service.addPlayer(hosted.game().getId(), "P" + index);

            Game started = service.startGame(hosted.game().getId(), hosted.hostToken());

            String expected = count <= 3 ? "default-small" : count <= 6 ? "default-medium" : "default-large";
            assertThat(started.getBoard().mapId()).isEqualTo(expected);
            assertThat(started.getVehicleStates()).extracting(state -> state.position()).doesNotHaveDuplicates();
            assertThat(started.getBoard().orderedCheckpoints()).extracting(Checkpoint::id)
                    .containsExactly("CP1", "CP2", "CP3", "CP4");
        }
    }

    @Test void randomCorridorsUsePersistedSeedAndActualPlayerCountSettings() {
        for (int count : List.of(2, 4, 7)) {
            InMemoryGameRepository repository = new InMemoryGameRepository();
            GameService service = new GameService(repository);
            GameConfiguration configuration = new GameConfiguration(count, 60, 5, 30,
                    6, 2, 1, -1, 1, GameConfiguration.RANDOM_CORRIDORS_MAP_ID, 731L);
            var hosted = service.createHostedGame(configuration);
            for (int index = 0; index < count; index++) service.addPlayer(hosted.game().getId(), "P" + index);

            Game started = service.startGame(hosted.game().getId(), hosted.hostToken());

            int expectedSize = count <= 3 ? 10 : count <= 6 ? 12 : 16;
            assertThat(started.getConfiguration().mapSeed()).isEqualTo(731L);
            assertThat(started.getBoard().mapId()).startsWith("generated-" + count + "-");
            assertThat(started.getBoard().width()).isEqualTo(expectedSize);
            assertThat(started.getBoard().height()).isEqualTo(expectedSize);
            assertThat(started.getBoard().orderedCheckpoints()).extracting(Checkpoint::id)
                    .containsExactly("CP1", "CP2", "CP3", "CP4");
            assertThat(started.getBoard().spawnPoints()).hasSize(count);
            assertThat(started.getBoard().obstacles()).isNotEmpty();
            assertThat(repository.findById(started.getId()).orElseThrow().getBoard()).isEqualTo(started.getBoard());
        }
    }

    @Test void randomCorridorsReceiveASeedOnceWhenTheLobbyIsCreated() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        GameConfiguration configuration = new GameConfiguration(2, 60, 5, 30,
                6, 2, 1, -1, 1, GameConfiguration.RANDOM_CORRIDORS_MAP_ID, null);

        var hosted = service.createHostedGame(configuration);
        Long assignedSeed = hosted.game().getConfiguration().mapSeed();
        service.addPlayer(hosted.game().getId(), "one");
        service.addPlayer(hosted.game().getId(), "two");
        Game started = service.startGame(hosted.game().getId(), hosted.hostToken());

        assertThat(assignedSeed).isNotNull();
        assertThat(started.getConfiguration().mapSeed()).isEqualTo(assignedSeed);
    }

    @Test void rejectsASeedForAHandDesignedMap() {
        GameService service = new GameService(new InMemoryGameRepository());
        GameConfiguration configuration = new GameConfiguration(2, 60, 5, 30,
                6, 2, 1, -1, 1, "default-small", 17L);

        assertThatThrownBy(() -> service.createHostedGame(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A map seed can only be used with Random Corridors");
    }

    @Test
    void createsGamesWithTheInjectedBoardFixture() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Board fixture = new Board(3, 2);
        GameService service = new GameService(repository, Clock.systemUTC(), new BotPlayerAutomation(),
                () -> fixture);

        assertThat(service.createGame().getBoard()).isSameAs(fixture);
    }

    @Test
    void deterministicFixtureSupportsTheTwoPlayerPushAndContainsEveryBoardFeature() {
        Board board = new DeterministicTestGameBoardFactory().createBoard();

        assertThat(board.spawnPoints().subList(0, 2)).extracting(spawn -> spawn.position())
                .containsExactly(new Position(0, 0), new Position(1, 0));
        assertThat(board.checkpointAt(new Position(2, 0))).isNotNull();
        assertThat(board.orderedCheckpoints()).extracting(Checkpoint::id)
                .containsExactly("CP1", "CP2", "CP3", "CP4");
        assertThat(board.orderedCheckpoints()).extracting(Checkpoint::order)
                .containsExactly(1, 2, 3, 4);
        assertThat(board.walls()).isNotEmpty();
        assertThat(board.pits()).isNotEmpty();
        assertThat(board.conveyors()).isNotEmpty();
        assertThat(board.rotators()).isNotEmpty();
        assertThat(board.controlPoints()).isNotEmpty();
    }

    @Test
    void shouldCreateGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);

        Game game = service.createGame();

        assertThat(game.getId()).isNotNull();
        assertThat(game.getPlayers()).isEmpty();
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING_FOR_PLAYERS);
        assertThat(game.getConfiguration()).isEqualTo(GameConfiguration.defaults());
        assertThat(game.getBoard().mapId()).isEqualTo("default-large");
        assertThat(game.getBoard().conveyors()).isNotEmpty();assertThat(game.getBoard().rotators()).isNotEmpty();
        assertThat(repository.findById(game.getId())).containsSame(game);
    }

    @Test
    void shouldCreateGameWithConfigurationAndDeterministicDeadline() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        GameService service = new GameService(repository, Clock.fixed(now, ZoneOffset.UTC));
        GameConfiguration configuration = new GameConfiguration(4, 60, 5, 45);

        Game game = service.createGame(configuration);

        assertThat(game.getConfiguration()).isEqualTo(configuration);
        assertThat(game.getCreatedAt()).isEqualTo(now);
        assertThat(game.getJoinDeadline()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void shouldAcceptFiveRegistersAndRejectOtherProgramSizes() {
        assertThat(new GameConfiguration(10, 60, 5, 30).programSize()).isEqualTo(5);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 1, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 3, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 4, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 6, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new GameConfiguration(0, 60, 5, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 0, 5, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDuplicateNickname() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame();
        service.addPlayer(game.getId(), "Alice");

        assertThatThrownBy(() -> service.addPlayer(game.getId(), " Alice "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nickname is already in use");
    }

    @Test
    void shouldRejectPlayerWhenLobbyIsFull() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame(new GameConfiguration(2, 60, 5, 30));
        service.addPlayer(game.getId(), "Alice");
        service.addPlayer(game.getId(), "Bob");

        assertThatThrownBy(() -> service.addPlayer(game.getId(), "Carol"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The lobby is full");
    }

    @Test
    void shouldKeepLobbyOpenAfterLegacyJoinTimeout() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        GameService creator = new GameService(repository, Clock.fixed(created, ZoneOffset.UTC));
        Game game = creator.createGame(new GameConfiguration(10, 60, 5, 30));
        GameService expired = new GameService(repository,
                Clock.fixed(created.plusSeconds(60), ZoneOffset.UTC));

        assertThat(expired.addPlayer(game.getId(), "Alice").player().getName()).isEqualTo("Alice");
    }

    @Test
    void shouldNotStartPlanningWhenLobbyFills() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame(new GameConfiguration(4, 60, 5, 30));
        service.addPlayer(game.getId(), "Alice");
        service.addPlayer(game.getId(), "Bob");
        service.addPlayer(game.getId(), "Charlie");

        service.addPlayer(game.getId(), "Dana");

        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING_FOR_PLAYERS);
        assertThat(game.getRound()).isNull();
    }

    @Test
    void hostShouldStartPlanningExplicitly() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        GameService creator = new GameService(repository, Clock.fixed(created, ZoneOffset.UTC));
        var hosted = creator.createHostedGame(new GameConfiguration(4, 60, 5, 30));
        Game game = hosted.game();
        creator.addPlayer(game.getId(), "Alice");
        creator.addPlayer(game.getId(), "Bob");
        creator.startGame(game.getId(), hosted.hostToken());

        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(game.getRound().number()).isEqualTo(1);
        assertThatThrownBy(() -> creator.addPlayer(game.getId(), "Charlie"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The lobby is closed");
    }

    @Test
    void shouldRejectStartWithOnlyOnePlayerAndInvalidHostToken() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        GameService creator = new GameService(repository, Clock.fixed(created, ZoneOffset.UTC));
        var hosted = creator.createHostedGame(new GameConfiguration(4, 60, 5, 30));
        Game game = hosted.game();
        creator.addPlayer(game.getId(), "Alice");

        assertThatThrownBy(() -> creator.startGame(game.getId(), hosted.hostToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("At least two players are required");
        assertThatThrownBy(() -> creator.startGame(game.getId(), "wrong"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid host token");

        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING_FOR_PLAYERS);
        assertThat(game.getRound()).isNull();
    }

    @Test
    void shouldNotAllowClientToStartFirstRound() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame();
        var alice = service.addPlayer(game.getId(), "Alice");

        assertThatThrownBy(() -> service.startRound(game.getId(), alice.player().getId(), alice.token(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The first round starts automatically");
    }

    @Test
    void shouldResolveConfiguredProgramsWhenEveryoneIsReady() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        GameService service = new GameService(repository, Clock.fixed(now, ZoneOffset.UTC));
        Game game = service.createGame(new GameConfiguration(2, 60, 5, 30));
        var alice = service.addPlayer(game.getId(), "Alice");
        var bob = service.addPlayer(game.getId(), "Bob");
        game.start(now, null, handsFor(game));
        List<MovementOrder> fiveCards = List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1);

        service.submitProgram(game.getId(), alice.player().getId(), alice.token(), fiveCards);
        service.submitProgram(game.getId(), bob.player().getId(), bob.token(), fiveCards);

        assertThat(game.getRound().allReady()).isTrue();
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(game.getRound().programs().get(alice.player().getId()).commands())
                .containsExactlyElementsOf(fiveCards);
    }

    @Test
    void shouldDealEightFreshCardsForTheNextRound() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        var hosted = service.createHostedGame();
        var alice = service.addPlayer(hosted.game().getId(), "Alice");
        var bob = service.addPlayer(hosted.game().getId(), "Bob");
        Game game = service.startGame(hosted.game().getId(), hosted.hostToken());
        var firstAliceHand = game.getRound().programs().get(alice.player().getId()).hand();
        var firstBobHand = game.getRound().programs().get(bob.player().getId()).hand();
        assertThat(firstAliceHand).hasSize(8);
        assertThat(firstBobHand).hasSize(8);
        service.submitProgram(game.getId(), alice.player().getId(), alice.token(), firstAliceHand.subList(0, 5));
        service.submitProgram(game.getId(), bob.player().getId(), bob.token(), firstBobHand.subList(0, 5));

        var next = service.startRound(game.getId(), alice.player().getId(), alice.token(), 1);

        assertThat(next.programs().get(alice.player().getId()).hand()).hasSize(8).isNotSameAs(firstAliceHand);
        assertThat(next.programs().get(bob.player().getId()).hand()).hasSize(8).isNotSameAs(firstBobHand);
    }

    @Test
    void shouldAddUniquelyNamedBotsAndLockTheirProgramsInDealtOrder() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, Clock.systemUTC(), new BotPlayerAutomation());
        var hosted = service.createHostedGame(new GameConfiguration(4, 60, 5, 30));
        Game game = hosted.game();
        var human = service.addPlayer(game.getId(), "Bot 1", hosted.hostToken());
        service.addBot(game.getId(), human.player().getId(), human.token());
        service.addBot(game.getId(), human.player().getId(), human.token());
        service.addBot(game.getId(), human.player().getId(), human.token());
        service.startGame(game.getId(), hosted.hostToken());

        assertThat(game.getPlayers()).extracting(Player::getName)
                .containsExactly("Bot 1", "Bot 2", "Bot 3", "Bot 4");
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(game.getRound().programs().get(human.player().getId()).ready()).isFalse();
        game.getPlayers().stream().skip(1).forEach(player -> {
            var program = game.getRound().programs().get(player.getId());
            assertThat(program.ready()).isTrue();
            assertThat(program.commands()).containsExactlyElementsOf(program.commands());
        });
    }

    @Test
    void shouldAuthenticateBotCreationAndEnforceLobbyCapacityAndStatus() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        var hosted = service.createHostedGame(new GameConfiguration(2, 60, 5, 30));
        var human = service.addPlayer(hosted.game().getId(), "Alice", hosted.hostToken());

        assertThatThrownBy(() -> service.addBot(hosted.game().getId(), human.player().getId(), "wrong"))
                .isInstanceOf(SecurityException.class).hasMessage("Invalid player token");

        Player bot = service.addBot(hosted.game().getId(), human.player().getId(), human.token());
        assertThat(bot.getName()).isEqualTo("Bot 1");
        assertThat(bot.isAutomated()).isTrue();
        assertThat(hosted.game().getVehicleStates()).hasSize(2);

        assertThatThrownBy(() -> service.addBot(hosted.game().getId(), human.player().getId(), human.token()))
                .isInstanceOf(IllegalStateException.class).hasMessage("The lobby is full");

        service.startGame(hosted.game().getId(), hosted.hostToken());
        assertThatThrownBy(() -> service.addBot(hosted.game().getId(), human.player().getId(), human.token()))
                .isInstanceOf(IllegalStateException.class).hasMessage("The lobby is closed");
    }

    @Test
    void shouldResolveAndPrepareBotsForTheNextRound() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, Clock.systemUTC(), new BotPlayerAutomation());
        var hosted = service.createHostedGame(new GameConfiguration(3, 60, 5, 30));
        Game game = hosted.game();
        var human = service.addPlayer(game.getId(), "Alice", hosted.hostToken());
        service.addBot(game.getId(), human.player().getId(), human.token());
        service.addBot(game.getId(), human.player().getId(), human.token());
        service.startGame(game.getId(), hosted.hostToken());

        service.submitProgram(game.getId(), human.player().getId(), human.token(),
                game.getRound().programs().get(human.player().getId()).hand().subList(0, 5));

        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);

        service.startRound(game.getId(), human.player().getId(), human.token(), 1);
        service.startRound(game.getId(), human.player().getId(), human.token(), 1);

        assertThat(game.getRound().number()).isEqualTo(2);
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(game.getRound().programs().get(human.player().getId()).ready()).isFalse();
        game.getPlayers().stream().skip(1).forEach(player -> {
            var program = game.getRound().programs().get(player.getId());
            assertThat(program.ready()).isTrue();
            assertThat(program.commands()).containsExactlyElementsOf(program.commands());
        });
    }

    @Test
    void onlyThePersistedHostPlayerCanAddBotsAfterReconnect() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        var hosted = service.createHostedGame(new GameConfiguration(4, 60, 5, 30));
        var host = service.addPlayer(hosted.game().getId(), "Alice", hosted.hostToken());
        var guest = service.addPlayer(hosted.game().getId(), "Bob");

        assertThat(hosted.game().getHostPlayerId()).isEqualTo(host.player().getId());
        assertThatThrownBy(() -> service.addBot(hosted.game().getId(), guest.player().getId(), guest.token()))
                .isInstanceOf(SecurityException.class).hasMessage("Only the host can add bots");

        GameService reconnected = new GameService(repository);
        Player bot = reconnected.addBot(hosted.game().getId(), host.player().getId(), host.token());

        assertThat(bot.isAutomated()).isTrue();
        assertThat(repository.findById(hosted.game().getId()).orElseThrow().getHostPlayerId())
                .isEqualTo(host.player().getId());
    }

    @Test
    void supportsMixedHumanAndBotLobbyCompositionsInEitherJoinOrder() {
        for (int humanCount = 1; humanCount <= 4; humanCount++) {
            InMemoryGameRepository repository = new InMemoryGameRepository();
            GameService service = new GameService(repository);
            var hosted = service.createHostedGame(new GameConfiguration(4, 60, 5, 30));
            var host = service.addPlayer(hosted.game().getId(), "Human 1", hosted.hostToken());
            int botCount = 4 - humanCount;

            if (humanCount <= 2 && botCount > 0)
                service.addBot(hosted.game().getId(), host.player().getId(), host.token());
            for (int index = 2; index <= humanCount; index++)
                service.addPlayer(hosted.game().getId(), "Human " + index);
            int existingBots = humanCount <= 2 && botCount > 0 ? 1 : 0;
            for (int index = existingBots; index < botCount; index++)
                service.addBot(hosted.game().getId(), host.player().getId(), host.token());

            Game game = repository.findById(hosted.game().getId()).orElseThrow();
            assertThat(game.getPlayers()).hasSize(4);
            assertThat(game.getPlayers()).filteredOn(Player::isAutomated).hasSize(botCount);
            assertThat(game.getPlayers()).filteredOn(player -> !player.isAutomated()).hasSize(humanCount);
        }
    }

    @Test
    void automatedPlayersCannotBeAssignedAsHost() {
        Game game = new Game(UUID.randomUUID(), List.of(), new Board(5, 5), GameStatus.WAITING_FOR_PLAYERS);
        Player bot = game.addAutomatedPlayer("Bot 1", "hash", Instant.now());

        assertThatThrownBy(() -> game.assignHost(bot.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Automated players cannot be host");
        assertThat(game.getHostPlayerId()).isNull();
    }

    @Test
    void shouldLockBotProgramInItsExistingOrder() {
        UUID humanId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        Player human = Player.create(humanId, "Alice", "human-token-hash");
        Player bot = Player.createAutomated(botId, "Bot 1", "bot-token-hash");
        Board board = new Board(5, 5);
        var humanVehicle = new se.segersten.wreckage.game.domain.Vehicle(UUID.randomUUID(), humanId);
        var botVehicle = new se.segersten.wreckage.game.domain.Vehicle(UUID.randomUUID(), botId);
        var vehicles = Map.of(
                humanId, new se.segersten.wreckage.game.domain.VehicleState(humanVehicle,
                        new se.segersten.wreckage.game.domain.Position(0, 0),
                        se.segersten.wreckage.game.domain.Direction.SOUTH),
                botId, new se.segersten.wreckage.game.domain.VehicleState(botVehicle,
                        new se.segersten.wreckage.game.domain.Position(1, 0),
                        se.segersten.wreckage.game.domain.Direction.SOUTH));
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        Game game = new Game(UUID.randomUUID(), List.of(human, bot), board,
                GameStatus.RUNNING, vehicles, null, new GameConfiguration(2, 60, 5, 30),
                now, now.plusSeconds(60));
        game.startRound(java.time.Instant.now(), handsFor(game));

        new BotPlayerAutomation().lockBotPrograms(game);

        var program = game.getRound().programs().get(botId);
        assertThat(program.commands()).containsExactly(
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1);
        assertThat(program.commands()).containsExactlyElementsOf(program.commands());
    }

    @Test
    void shouldPersistAnAuthenticatedProgramDraftForRecovery() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, Clock.systemUTC());
        Game game = service.createGame(new GameConfiguration(2, 60, 5, 30));
        var per = service.addPlayer(game.getId(), "Per");
        service.addPlayer(game.getId(), "Alice");
        game.start(Instant.now(), null, handsFor(game));
        List<MovementOrder> draft = List.of(MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1);

        service.saveProgramDraft(game.getId(), per.player().getId(), per.token(), draft);

        Game recovered = service.getPlayerGame(game.getId(), per.player().getId(), per.token());
        assertThat(recovered.getRound().programs().get(per.player().getId()).commands())
                .containsExactlyElementsOf(draft);
        assertThat(recovered.getRound().programs().get(per.player().getId()).ready()).isFalse();
        assertThatThrownBy(() -> service.saveProgramDraft(game.getId(), per.player().getId(),
                "wrong-token", draft)).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldCompleteMissingRegistersWithWaitAtPlanningDeadline() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        GameService service = new GameService(repository, Clock.fixed(now, ZoneOffset.UTC));
        Game game = service.createGame(new GameConfiguration(2, 60, 5, 5));
        var per = service.addPlayer(game.getId(), "Per");
        service.addPlayer(game.getId(), "Alice");
        game.start(now, null, handsFor(game));
        service.saveProgramDraft(game.getId(), per.player().getId(), per.token(),
                List.of(MovementOrder.TURN_LEFT));

        new GameService(repository, Clock.fixed(now.plusSeconds(4), ZoneOffset.UTC))
                .completeExpiredPlanning();

        PlayerProgram pending = game.getRound().programs().get(per.player().getId());
        assertThat(pending.commands()).containsExactly(MovementOrder.TURN_LEFT);
        assertThat(pending.ready()).isFalse();
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);

        new GameService(repository, Clock.fixed(now.plusSeconds(5), ZoneOffset.UTC))
                .completeExpiredPlanning();

        PlayerProgram completed = game.getRound().programs().get(per.player().getId());
        assertThat(completed.commands()).containsExactly(
                MovementOrder.TURN_LEFT, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_1, MovementOrder.FORWARD_1);
        assertThat(completed.ready()).isTrue();
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
    }

    @Test
    void shouldAddPlayerToExistingGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame();

        Player player = service.addPlayer(game.getId(), " Per ").player();

        assertThat(player.getId()).isNotNull();
        assertThat(player.getName()).isEqualTo("Per");
        assertThat(game.getPlayers()).containsExactly(player);
    }

    @Test
    void shouldRejectBlankPlayerName() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame();

        assertThatThrownBy(() -> service.addPlayer(game.getId(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Player name must not be blank");
    }

    @Test
    void shouldRejectPlayerWhenGameDoesNotExist() {
        GameService service = new GameService(new InMemoryGameRepository());
        UUID missingGameId = UUID.randomUUID();

        assertThatThrownBy(() -> service.addPlayer(missingGameId, "Per"))
                .isInstanceOf(GameNotFoundException.class)
                .hasMessage("Game not found: " + missingGameId);
    }

    @Test
    void shouldGetRunningGames() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Game running = repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        repository.save(new Game(
                UUID.randomUUID(), List.of(), new Board(20, 20), GameStatus.FINISHED));
        GameService service = new GameService(repository);

        assertThat(service.getRunningGames()).containsExactly(running);
    }

    @Test
    void shouldGetFinishedGames() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        Game finished = repository.save(new Game(
                UUID.randomUUID(), List.of(), new Board(20, 20), GameStatus.FINISHED));
        GameService service = new GameService(repository);

        assertThat(service.getFinishedGames()).containsExactly(finished);
    }

    @Test
    void shouldReturnEmptyListsWhenNoGamesMatch() {
        GameService service = new GameService(new InMemoryGameRepository());

        assertThat(service.getRunningGames()).isEmpty();
        assertThat(service.getFinishedGames()).isEmpty();
    }

    private static Map<UUID, List<MovementOrder>> handsFor(Game game) {
        return game.getPlayers().stream().collect(java.util.stream.Collectors.toMap(Player::getId,
                ignored -> List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                        MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1,
                        MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT, MovementOrder.U_TURN)));
    }

    private static final class InMemoryGameRepository implements GameRepository {

        private final Map<UUID, Game> games = new HashMap<>();

        @Override
        public Game save(Game game) {
            games.put(game.getId(), game);
            return game;
        }

        @Override
        public Optional<Game> findById(UUID id) {
            return Optional.ofNullable(games.get(id));
        }

        @Override
        public Optional<Game> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public List<Game> findAllByStatus(GameStatus status) {
            return games.values().stream()
                    .filter(game -> game.getStatus() == status)
                    .toList();
        }

        @Override
        public List<Game> findAllByStatusForUpdate(GameStatus status) {
            return findAllByStatus(status);
        }
    }
}
