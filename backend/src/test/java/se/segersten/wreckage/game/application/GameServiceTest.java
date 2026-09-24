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
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;

class GameServiceTest {

    @Test
    void shouldCreateGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);

        Game game = service.createGame();

        assertThat(game.getId()).isNotNull();
        assertThat(game.getPlayers()).isEmpty();
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING_FOR_PLAYERS);
        assertThat(game.getConfiguration()).isEqualTo(GameConfiguration.defaults());
        assertThat(game.getBoard()).isEqualTo(new Board(20, 20, java.util.Set.of(),
                java.util.Set.of(new se.segersten.wreckage.game.domain.Position(4, 5))));
        assertThat(repository.findById(game.getId())).containsSame(game);
    }

    @Test
    void shouldCreateGameWithConfigurationAndDeterministicDeadline() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        GameService service = new GameService(repository, Clock.fixed(now, ZoneOffset.UTC));
        GameConfiguration configuration = new GameConfiguration(4, 60, 10, 45);

        Game game = service.createGame(configuration);

        assertThat(game.getConfiguration()).isEqualTo(configuration);
        assertThat(game.getCreatedAt()).isEqualTo(now);
        assertThat(game.getJoinDeadline()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void shouldAcceptCardsPerRoundBoundaryValues() {
        assertThat(new GameConfiguration(12, 60, 3, 30).cardsPerRound()).isEqualTo(3);
        assertThat(new GameConfiguration(12, 60, 10, 30).cardsPerRound()).isEqualTo(10);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new GameConfiguration(0, 60, 3, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(12, 0, 3, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(12, 60, 2, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(12, 60, 11, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfiguration(12, 60, 3, 0))
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
        Game game = service.createGame(new GameConfiguration(1, 60, 3, 30));
        service.addPlayer(game.getId(), "Alice");

        assertThatThrownBy(() -> service.addPlayer(game.getId(), "Bob"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The lobby is full");
    }

    @Test
    void shouldRejectPlayerAfterJoinTimeout() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        GameService creator = new GameService(repository, Clock.fixed(created, ZoneOffset.UTC));
        Game game = creator.createGame(new GameConfiguration(12, 60, 3, 30));
        GameService expired = new GameService(repository,
                Clock.fixed(created.plusSeconds(60), ZoneOffset.UTC));

        assertThatThrownBy(() -> expired.addPlayer(game.getId(), "Alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The lobby is closed");
    }

    @Test
    void shouldStartPlanningWhenFourthPlayerFillsLobby() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame(new GameConfiguration(4, 60, 3, 30));
        service.addPlayer(game.getId(), "Alice");
        service.addPlayer(game.getId(), "Bob");
        service.addPlayer(game.getId(), "Charlie");

        service.addPlayer(game.getId(), "Dana");

        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
    }

    @Test
    void shouldStartPlanningAtDeadlineWhenAtLeastTwoPlayersJoined() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        GameService creator = new GameService(repository, Clock.fixed(created, ZoneOffset.UTC));
        Game game = creator.createGame(new GameConfiguration(4, 60, 3, 30));
        creator.addPlayer(game.getId(), "Alice");
        creator.addPlayer(game.getId(), "Bob");
        GameService expired = new GameService(repository,
                Clock.fixed(created.plusSeconds(60), ZoneOffset.UTC));

        expired.startExpiredLobbies();
        expired.startExpiredLobbies();

        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(game.getRound().number()).isEqualTo(1);
        assertThatThrownBy(() -> expired.addPlayer(game.getId(), "Charlie"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The lobby is closed");
    }

    @Test
    void shouldNotStartAtDeadlineWithOnlyOnePlayer() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        GameService creator = new GameService(repository, Clock.fixed(created, ZoneOffset.UTC));
        Game game = creator.createGame(new GameConfiguration(4, 60, 3, 30));
        creator.addPlayer(game.getId(), "Alice");

        new GameService(repository, Clock.fixed(created.plusSeconds(60), ZoneOffset.UTC))
                .startExpiredLobbies();

        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING_FOR_PLAYERS);
        assertThat(game.getRound()).isNull();
    }

    @Test
    void shouldNotAllowClientToStartFirstRound() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);
        Game game = service.createGame();
        var alice = service.addPlayer(game.getId(), "Alice");

        assertThatThrownBy(() -> service.startRound(game.getId(), alice.player().getId(), alice.token()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The first round starts automatically");
    }

    @Test
    void shouldResolveConfiguredProgramsWhenEveryoneIsReady() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        GameService service = new GameService(repository, Clock.fixed(now, ZoneOffset.UTC),
                () -> MovementOrder.FORWARD);
        Game game = service.createGame(new GameConfiguration(2, 60, 5, 30));
        var alice = service.addPlayer(game.getId(), "Alice");
        var bob = service.addPlayer(game.getId(), "Bob");
        List<MovementOrder> fiveCards = List.of(MovementOrder.FORWARD, MovementOrder.FORWARD,
                MovementOrder.FORWARD, MovementOrder.FORWARD, MovementOrder.FORWARD);

        service.submitProgram(game.getId(), alice.player().getId(), alice.token(), fiveCards);
        service.submitProgram(game.getId(), bob.player().getId(), bob.token(), fiveCards);

        assertThat(game.getRound().allReady()).isTrue();
        assertThat(game.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(game.getRound().playback()).extracting(RoundEvent::type)
                .containsExactly(RoundEventType.FIRE, RoundEventType.FIRE);
        assertThat(game.getRound().programs().get(alice.player().getId()).orders())
                .containsExactlyElementsOf(fiveCards);
    }

    @Test
    void shouldAddPlayerToExistingGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Game game = repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        GameService service = new GameService(repository);

        Player player = service.addPlayer(game.getId(), " Per ").player();

        assertThat(player.getId()).isNotNull();
        assertThat(player.getName()).isEqualTo("Per");
        assertThat(game.getPlayers()).containsExactly(player);
    }

    @Test
    void shouldRejectBlankPlayerName() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Game game = repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        GameService service = new GameService(repository);

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
