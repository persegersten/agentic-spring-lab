package se.segersten.wreckage.game.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameStatus;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.engine.MovementEngine;

@Service
@Transactional
public class GameService {

    private static final int DEFAULT_BOARD_WIDTH = 20;
    private static final int DEFAULT_BOARD_HEIGHT = 20;

    private final GameRepository gameRepository;
    private final Clock clock;

    @Autowired
    public GameService(GameRepository gameRepository) {
        this(gameRepository, Clock.systemUTC());
    }

    GameService(GameRepository gameRepository, Clock clock) {
        this.gameRepository = gameRepository;
        this.clock = clock;
    }

    public Game createGame() {
        return createGame(GameConfiguration.defaults());
    }

    public Game createGame(GameConfiguration configuration) {
        Board board = new Board(DEFAULT_BOARD_WIDTH, DEFAULT_BOARD_HEIGHT);
        Instant createdAt = clock.instant();
        return gameRepository.save(new Game(UUID.randomUUID(), List.of(), board,
                GameStatus.WAITING_FOR_PLAYERS, Map.of(), null, configuration, createdAt,
                createdAt.plusSeconds(configuration.joinTimeoutSeconds())));
    }

    public PlayerJoin addPlayer(UUID gameId, String name) {
        Game game = findGame(gameId);
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        Player player = game.addPlayer(name, hash(token), clock.instant());
        gameRepository.save(game);
        return new PlayerJoin(player, token);
    }

    public Round startRound(UUID gameId, UUID playerId, String token) {
        Game game = authenticatedGame(gameId, playerId, token);
        Round round = game.startRound(() -> {
            MovementOrder[] cards = MovementOrder.values();
            return cards[ThreadLocalRandom.current().nextInt(cards.length)];
        });
        gameRepository.save(game);
        return round;
    }

    public Game getPlayerGame(UUID gameId, UUID playerId, String token) {
        return authenticatedGame(gameId, playerId, token);
    }

    public Round submitProgram(UUID gameId, UUID playerId, String token, List<MovementOrder> orders) {
        Game game = authenticatedGame(gameId, playerId, token);
        if (game.getRound() == null) throw new IllegalStateException("No round has started");
        game.getRound().lock(playerId, orders);
        if (game.getRound().allReady()) game.getRound().resolve(new MovementEngine());
        gameRepository.save(game);
        return game.getRound();
    }

    @Transactional(readOnly = true)
    public Game getGame(UUID gameId) {
        return findGame(gameId);
    }

    @Transactional(readOnly = true)
    public List<Game> getRunningGames() {
        List<Game> games = new java.util.ArrayList<>(gameRepository.findAllByStatus(GameStatus.WAITING_FOR_PLAYERS));
        games.addAll(gameRepository.findAllByStatus(GameStatus.RUNNING));
        return List.copyOf(games);
    }

    @Transactional(readOnly = true)
    public List<Game> getFinishedGames() {
        return gameRepository.findAllByStatus(GameStatus.FINISHED);
    }

    private Game findGame(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    private Game authenticatedGame(UUID gameId, UUID playerId, String token) {
        Game game = findGame(gameId);
        Player player = game.requirePlayer(playerId);
        if (token == null || !MessageDigest.isEqual(
                player.getAccessTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(token).getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid player token");
        }
        return game;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record PlayerJoin(Player player, String token) {}
}
