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
    void createsGamesWithTheInjectedBoardFixture() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Board fixture = new Board(3, 2);
        GameService service = new GameService(repository, Clock.systemUTC(), new NoOpPlayerAutomation(),
                () -> fixture);

        assertThat(service.createGame().getBoard()).isSameAs(fixture);
    }

    @Test
    void deterministicFixtureSupportsTheTwoPlayerPushAndContainsEveryBoardFeature() {
        Board board = new DeterministicTestGameBoardFactory().createBoard();

        assertThat(board.spawnPoints().subList(0, 2)).extracting(spawn -> spawn.position())
                .containsExactly(new Position(0, 0), new Position(1, 0));
        assertThat(board.checkpointAt(new Position(2, 0))).isNotNull();
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
        assertThat(game.getBoard().conveyors()).hasSize(1);assertThat(game.getBoard().rotators()).hasSize(1);
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
    void shouldAcceptProgramSizeBoundaryValues() {
        assertThat(new GameConfiguration(10, 60, 1, 30).programSize()).isEqualTo(1);
        assertThat(new GameConfiguration(10, 60, 5, 30).programSize()).isEqualTo(5);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new GameConfiguration(0, 60, 3, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 0, 3, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 0, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 6, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(10, 60, 3, 0))
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
        Game game = service.createGame(new GameConfiguration(2, 60, 3, 30));
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
        Game game = creator.createGame(new GameConfiguration(10, 60, 3, 30));
        GameService expired = new GameService(repository,
                Clock.fixed(created.plusSeconds(60), ZoneOffset.UTC));

        assertThat(expired.addPlayer(game.getId(), "Alice").player().getName()).isEqualTo("Alice");
    }

    @Test
    void shouldNotStartPlanningWhenLobbyFills() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame(new GameConfiguration(4, 60, 3, 30));
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
        var hosted = creator.createHostedGame(new GameConfiguration(4, 60, 3, 30));
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
        var hosted = creator.createHostedGame(new GameConfiguration(4, 60, 3, 30));
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
        game.start(now);
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
    void shouldFillLobbyAndLockHeadlessProgramsInDealtOrder() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, Clock.systemUTC(), new HeadlessPlayerAutomation());
        var hosted = service.createHostedGame(new GameConfiguration(4, 60, 3, 30));
        Game game = hosted.game();
        var human = service.addPlayer(game.getId(), "Headless 1");
        service.addHeadlessPlayers(); service.addHeadlessPlayers(); service.addHeadlessPlayers();
        service.startGame(game.getId(), hosted.hostToken());

        assertThat(game.getPlayers()).extracting(Player::getName)
                .containsExactly("Headless 1", "Headless 2", "Headless 3", "Headless 4");
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(game.getRound().programs().get(human.player().getId()).ready()).isFalse();
        game.getPlayers().stream().skip(1).forEach(player -> {
            var program = game.getRound().programs().get(player.getId());
            assertThat(program.ready()).isTrue();
            assertThat(program.commands()).containsExactlyElementsOf(program.commands());
        });
    }

    @Test
    void shouldResolveAndPrepareHeadlessPlayersForTheNextRound() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, Clock.systemUTC(), new HeadlessPlayerAutomation());
        var hosted = service.createHostedGame(new GameConfiguration(3, 60, 3, 30));
        Game game = hosted.game();
        var human = service.addPlayer(game.getId(), "Alice");
        service.addHeadlessPlayers(); service.addHeadlessPlayers();
        service.startGame(game.getId(), hosted.hostToken());

        service.submitProgram(game.getId(), human.player().getId(), human.token(),
                java.util.Collections.nCopies(game.getConfiguration().programSize(), MovementOrder.WAIT));

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
    void shouldLockHeadlessProgramInItsExistingOrder() {
        UUID humanId = UUID.randomUUID();
        UUID headlessId = UUID.randomUUID();
        Player human = Player.create(humanId, "Alice", "human-token-hash");
        Player headless = Player.createAutomated(headlessId, "Headless 1", "headless-token-hash");
        Board board = new Board(5, 5);
        var humanVehicle = new se.segersten.wreckage.game.domain.Vehicle(UUID.randomUUID(), humanId);
        var headlessVehicle = new se.segersten.wreckage.game.domain.Vehicle(UUID.randomUUID(), headlessId);
        var vehicles = Map.of(
                humanId, new se.segersten.wreckage.game.domain.VehicleState(humanVehicle,
                        new se.segersten.wreckage.game.domain.Position(0, 0),
                        se.segersten.wreckage.game.domain.Direction.SOUTH),
                headlessId, new se.segersten.wreckage.game.domain.VehicleState(headlessVehicle,
                        new se.segersten.wreckage.game.domain.Position(1, 0),
                        se.segersten.wreckage.game.domain.Direction.SOUTH));
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        Game game = new Game(UUID.randomUUID(), List.of(human, headless), board,
                GameStatus.RUNNING, vehicles, null, new GameConfiguration(2, 60, 3, 30),
                now, now.plusSeconds(60));
        game.startRound(java.time.Instant.now());

        new HeadlessPlayerAutomation().lockHeadlessPrograms(game);

        var program = game.getRound().programs().get(headlessId);
        assertThat(program.commands()).containsExactly(MovementOrder.WAIT,
                MovementOrder.WAIT, MovementOrder.WAIT);
        assertThat(program.commands()).containsExactlyElementsOf(program.commands());
    }

    @Test
    void shouldPersistAnAuthenticatedProgramDraftForRecovery() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository, Clock.systemUTC());
        Game game = service.createGame(new GameConfiguration(2, 60, 3, 30));
        var per = service.addPlayer(game.getId(), "Per");
        service.addPlayer(game.getId(), "Alice");
        game.start(Instant.now());
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
        Game game = service.createGame(new GameConfiguration(2, 60, 3, 5));
        var per = service.addPlayer(game.getId(), "Per");
        service.addPlayer(game.getId(), "Alice");
        game.start(now);
        service.saveProgramDraft(game.getId(), per.player().getId(), per.token(),
                List.of(MovementOrder.TURN_LEFT));

        new GameService(repository, Clock.fixed(now.plusSeconds(5), ZoneOffset.UTC))
                .completeExpiredPlanning();

        PlayerProgram completed = game.getRound().programs().get(per.player().getId());
        assertThat(completed.commands()).containsExactly(
                MovementOrder.TURN_LEFT, MovementOrder.WAIT, MovementOrder.WAIT);
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
