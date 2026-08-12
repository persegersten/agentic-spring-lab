package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.Player;

class GameServiceTest {

    @Test
    void shouldCreateGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        GameService service = new GameService(repository);

        Game game = service.createGame();

        assertThat(game.getId()).isNotNull();
        assertThat(game.getPlayers()).isEmpty();
        assertThat(game.getBoard()).isEqualTo(new Board(20, 20));
        assertThat(repository.findById(game.getId())).containsSame(game);
    }

    @Test
    void shouldAddPlayerToExistingGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Game game = repository.save(new Game(UUID.randomUUID(), new Board(20, 20)));
        GameService service = new GameService(repository);

        Player player = service.addPlayer(game.getId(), " Per ");

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
    }
}
