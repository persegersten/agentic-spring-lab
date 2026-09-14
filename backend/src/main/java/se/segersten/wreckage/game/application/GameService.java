package se.segersten.wreckage.game.application;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameStatus;
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

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public Game createGame() {
        Board board = new Board(DEFAULT_BOARD_WIDTH, DEFAULT_BOARD_HEIGHT);
        return gameRepository.save(new Game(UUID.randomUUID(), board));
    }

    public PlayerJoin addPlayer(UUID gameId, String name) {
        Game game = findGame(gameId);
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        Player player = game.addPlayer(name, hash(token));
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
        return gameRepository.findAllByStatus(GameStatus.RUNNING);
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
