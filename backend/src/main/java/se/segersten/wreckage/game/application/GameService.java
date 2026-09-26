package se.segersten.wreckage.game.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
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
    private final Supplier<MovementOrder> cardSource;
    private final PlayerAutomation playerAutomation;

    @Autowired
    public GameService(GameRepository gameRepository, PlayerAutomation playerAutomation) {
        this(gameRepository, Clock.systemUTC(), GameService::randomCard, playerAutomation);
    }

    public GameService(GameRepository gameRepository) {
        this(gameRepository, Clock.systemUTC(), GameService::randomCard, new NoOpPlayerAutomation());
    }

    GameService(GameRepository gameRepository, Clock clock) {
        this(gameRepository, clock, GameService::randomCard, new NoOpPlayerAutomation());
    }

    GameService(GameRepository gameRepository, Clock clock, Supplier<MovementOrder> cardSource) {
        this(gameRepository, clock, cardSource, new NoOpPlayerAutomation());
    }

    GameService(GameRepository gameRepository, Clock clock, Supplier<MovementOrder> cardSource,
                PlayerAutomation playerAutomation) {
        this.gameRepository = gameRepository;
        this.clock = clock;
        this.cardSource = java.util.Objects.requireNonNull(cardSource);
        this.playerAutomation = java.util.Objects.requireNonNull(playerAutomation);
    }

    public Game createGame() {
        return createGame(GameConfiguration.defaults());
    }

    public Game createGame(GameConfiguration configuration) {
        Board board = new Board(DEFAULT_BOARD_WIDTH, DEFAULT_BOARD_HEIGHT, java.util.Set.of(),
                java.util.Set.of(new se.segersten.wreckage.game.domain.Position(4, 5)));
        Instant createdAt = clock.instant();
        return gameRepository.save(new Game(UUID.randomUUID(), List.of(), board,
                GameStatus.WAITING_FOR_PLAYERS, Map.of(), null, configuration, createdAt,
                createdAt.plusSeconds(configuration.joinTimeoutSeconds())));
    }

    public PlayerJoin addPlayer(UUID gameId, String name) {
        Game game = findGameForUpdate(gameId);
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        Instant now = clock.instant();
        Player player = game.addPlayer(name, hash(token), now);
        playerAutomation.fillLobby(game, now);
        game.startIfReady(now, cardSource);
        playerAutomation.lockHeadlessPrograms(game);
        resolveIfReady(game);
        gameRepository.save(game);
        return new PlayerJoin(player, token);
    }

    public Round startRound(UUID gameId, UUID playerId, String token) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        if (game.getRound() == null)
            throw new IllegalStateException("The first round starts automatically");
        Round round = game.startRound(cardSource);
        playerAutomation.lockHeadlessPrograms(game);
        resolveIfReady(game);
        gameRepository.save(game);
        return round;
    }

    public void startExpiredLobbies() {
        Instant now = clock.instant();
        for (Game game : gameRepository.findAllByStatusForUpdate(GameStatus.WAITING_FOR_PLAYERS)) {
            if (game.startIfReady(now, cardSource)) gameRepository.save(game);
        }
    }

    public Game getPlayerGame(UUID gameId, UUID playerId, String token) {
        return authenticatedGame(gameId, playerId, token);
    }

    public Round saveProgramDraft(UUID gameId, UUID playerId, String token, List<MovementOrder> orders) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        if (game.getRound() == null) throw new IllegalStateException("No round has started");
        game.getRound().reorder(playerId, orders);
        gameRepository.save(game);
        return game.getRound();
    }

    public Round submitProgram(UUID gameId, UUID playerId, String token, List<MovementOrder> orders) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
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

    private void resolveIfReady(Game game) {
        Round round = game.getRound();
        if (round != null && round.phase() == se.segersten.wreckage.game.domain.RoundPhase.PLANNING
                && round.allReady()) round.resolve(new MovementEngine());
    }

    private Game findGame(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    private Game findGameForUpdate(UUID gameId) {
        return gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    private Game authenticatedGame(UUID gameId, UUID playerId, String token) {
        Game game = findGame(gameId);
        authenticate(game, playerId, token);
        return game;
    }

    private Game authenticatedGameForUpdate(UUID gameId, UUID playerId, String token) {
        Game game = findGameForUpdate(gameId);
        authenticate(game, playerId, token);
        return game;
    }

    private void authenticate(Game game, UUID playerId, String token) {
        Player player = game.requirePlayer(playerId);
        if (token == null || !MessageDigest.isEqual(
                player.getAccessTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(token).getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid player token");
        }
    }

    private static MovementOrder randomCard() {
        MovementOrder[] cards = { MovementOrder.FORWARD, MovementOrder.REVERSE,
                MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT };
        return cards[ThreadLocalRandom.current().nextInt(cards.length)];
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record PlayerJoin(Player player, String token) {}
}
