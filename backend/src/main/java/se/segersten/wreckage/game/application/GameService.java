package se.segersten.wreckage.game.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    private final GameRepository gameRepository;
    private final Clock clock;
    private final PlayerAutomation playerAutomation;
    private final GameBoardFactory gameBoardFactory;

    @Autowired
    public GameService(GameRepository gameRepository, PlayerAutomation playerAutomation,
                       GameBoardFactory gameBoardFactory) {
        this(gameRepository, Clock.systemUTC(), playerAutomation, gameBoardFactory);
    }

    public GameService(GameRepository gameRepository) {
        this(gameRepository, Clock.systemUTC(), new NoOpPlayerAutomation(), new DefaultGameBoardFactory());
    }

    GameService(GameRepository gameRepository, Clock clock) {
        this(gameRepository, clock, new NoOpPlayerAutomation(), new DefaultGameBoardFactory());
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation) {
        this(gameRepository, clock, playerAutomation, new DefaultGameBoardFactory());
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation,
                GameBoardFactory gameBoardFactory) {
        this.gameRepository = gameRepository;
        this.clock = clock;
        this.playerAutomation = java.util.Objects.requireNonNull(playerAutomation);
        this.gameBoardFactory = java.util.Objects.requireNonNull(gameBoardFactory);
    }

    public Game createGame() {
        return createHostedGame(GameConfiguration.defaults()).game();
    }

    public Game createGame(GameConfiguration configuration) {
        return createHostedGame(configuration).game();
    }

    public HostedGame createHostedGame() {
        return createHostedGame(GameConfiguration.defaults());
    }

    public HostedGame createHostedGame(GameConfiguration configuration) {
        Board board = gameBoardFactory.createBoard();
        Instant createdAt = clock.instant();
        String hostToken = UUID.randomUUID().toString() + UUID.randomUUID();
        Game game = gameRepository.save(new Game(UUID.randomUUID(), List.of(), board,
                GameStatus.WAITING_FOR_PLAYERS, Map.of(), null, configuration, createdAt,
                createdAt.plusSeconds(configuration.joinTimeoutSeconds()), hash(hostToken)));
        return new HostedGame(game, hostToken);
    }

    public PlayerJoin addPlayer(UUID gameId, String name) {
        Game game = findGameForUpdate(gameId);
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        Instant now = clock.instant();
        Player player = game.addPlayer(name, hash(token), now);
        gameRepository.save(game);
        return new PlayerJoin(player, token);
    }

    public Game startGame(UUID gameId, String hostToken) {
        Game game = findGameForUpdate(gameId);
        authenticateHost(game, hostToken);
        game.start(clock.instant());
        playerAutomation.lockHeadlessPrograms(game);
        resolveIfReady(game);
        return gameRepository.save(game);
    }

    public Round startRound(UUID gameId, UUID playerId, String token, int completedRoundNumber) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        if (game.getRound() == null)
            throw new IllegalStateException("The first round starts automatically");
        if (game.getRound().number() > completedRoundNumber)
            return game.getRound();
        if (game.getRound().number() < completedRoundNumber)
            throw new IllegalArgumentException("The completed round does not exist");
        Round round = game.startRound(clock.instant());
        playerAutomation.lockHeadlessPrograms(game);
        resolveIfReady(game);
        gameRepository.save(game);
        return round;
    }

    public void addHeadlessPlayers() {
        Instant now = clock.instant();
        for (Game game : gameRepository.findAllByStatusForUpdate(GameStatus.WAITING_FOR_PLAYERS)) {
            if (playerAutomation.addHeadlessPlayer(game, now)) gameRepository.save(game);
        }
    }
    public void completeExpiredPlanning(){Instant now=clock.instant();for(Game game:gameRepository.findAllByStatusForUpdate(GameStatus.RUNNING)){Round round=game.getRound();if(round!=null&&round.completeTimedOutPrograms(now)){resolveIfReady(game);gameRepository.save(game);}}}

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
        resolveIfReady(game);
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
                &&round.allReady()){var movement=new MovementEngine();round.resolve(movement,new se.segersten.wreckage.game.engine.BoardEffectEngine(movement),game.getPlayers(),game.getConfiguration());}
        game.completeRound();
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

    private void authenticateHost(Game game, String token) {
        if (token == null || !MessageDigest.isEqual(
                game.getHostTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(token).getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid host token");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record PlayerJoin(Player player, String token) {}
    public record HostedGame(Game game, String hostToken) {}
}
