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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameStatus;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.CorridorGenerationParameters;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.engine.MovementEngine;

@Service
@Transactional
public class GameService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameService.class);

    static final int DEFAULT_MAX_CONCURRENT_GAMES = 10;

    private final GameRepository gameRepository;
    private final Clock clock;
    private final PlayerAutomation playerAutomation;
    private final GameBoardFactory gameBoardFactory;
    private final MapRepository mapRepository;
    private final ProgrammingCardDealer cardDealer;
    private final PlayableMapGenerator playableMapGenerator;
    private final CorridorAsciiFormatter corridorAsciiFormatter;
    private final int maxConcurrentGames;

    @Autowired
    public GameService(GameRepository gameRepository, PlayerAutomation playerAutomation,
                       GameBoardFactory gameBoardFactory, MapRepository mapRepository,
                       ProgrammingCardDealer cardDealer, PlayableMapGenerator playableMapGenerator,
                       @Value("${wreckage.games.max-concurrent:10}") int maxConcurrentGames) {
        this(gameRepository, Clock.systemUTC(), playerAutomation, gameBoardFactory, mapRepository, cardDealer,
                playableMapGenerator, new CorridorAsciiFormatter(), maxConcurrentGames);
    }

    public GameService(GameRepository gameRepository) {
        this(gameRepository, DEFAULT_MAX_CONCURRENT_GAMES);
    }

    GameService(GameRepository gameRepository, int maxConcurrentGames) {
        this(gameRepository, Clock.systemUTC(), new BotPlayerAutomation(), null, localMaps(), defaultDealer(),
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), maxConcurrentGames);
    }

    GameService(GameRepository gameRepository, Clock clock) {
        this(gameRepository, clock, new BotPlayerAutomation(), null, localMaps(), defaultDealer(),
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), DEFAULT_MAX_CONCURRENT_GAMES);
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation) {
        this(gameRepository, clock, playerAutomation, null, localMaps(), defaultDealer(),
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), DEFAULT_MAX_CONCURRENT_GAMES);
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation,
                GameBoardFactory gameBoardFactory) {
        this(gameRepository, clock, playerAutomation, gameBoardFactory, localMaps(), defaultDealer(),
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), DEFAULT_MAX_CONCURRENT_GAMES);
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation,
                GameBoardFactory gameBoardFactory, MapRepository mapRepository) {
        this(gameRepository, clock, playerAutomation, gameBoardFactory, mapRepository, defaultDealer(),
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), DEFAULT_MAX_CONCURRENT_GAMES);
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation,
                GameBoardFactory gameBoardFactory, MapRepository mapRepository, ProgrammingCardDealer cardDealer) {
        this(gameRepository, clock, playerAutomation, gameBoardFactory, mapRepository, cardDealer,
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), DEFAULT_MAX_CONCURRENT_GAMES);
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation,
                GameBoardFactory gameBoardFactory, MapRepository mapRepository, ProgrammingCardDealer cardDealer,
                int maxConcurrentGames) {
        this(gameRepository, clock, playerAutomation, gameBoardFactory, mapRepository, cardDealer,
                new PlayableMapGenerator(), new CorridorAsciiFormatter(), maxConcurrentGames);
    }

    GameService(GameRepository gameRepository, Clock clock, PlayerAutomation playerAutomation,
                GameBoardFactory gameBoardFactory, MapRepository mapRepository, ProgrammingCardDealer cardDealer,
                PlayableMapGenerator playableMapGenerator, CorridorAsciiFormatter corridorAsciiFormatter,
                int maxConcurrentGames) {
        if (maxConcurrentGames < 1) throw new IllegalArgumentException("maxConcurrentGames must be positive");
        this.gameRepository = gameRepository;
        this.clock = clock;
        this.playerAutomation = java.util.Objects.requireNonNull(playerAutomation);
        this.mapRepository = java.util.Objects.requireNonNull(mapRepository);
        this.gameBoardFactory = gameBoardFactory;
        this.cardDealer = java.util.Objects.requireNonNull(cardDealer);
        this.playableMapGenerator = java.util.Objects.requireNonNull(playableMapGenerator);
        this.corridorAsciiFormatter = java.util.Objects.requireNonNull(corridorAsciiFormatter);
        this.maxConcurrentGames = maxConcurrentGames;
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

    public synchronized HostedGame createHostedGame(GameConfiguration configuration) {
        int activeGames = gameRepository.findAllByStatus(GameStatus.WAITING_FOR_PLAYERS).size()
                + gameRepository.findAllByStatus(GameStatus.RUNNING).size();
        if (activeGames >= maxConcurrentGames)
            throw new IllegalStateException("The game server has reached its concurrent game limit");
        boolean randomCorridors = GameConfiguration.RANDOM_CORRIDORS_MAP_ID.equals(configuration.mapId());
        if (configuration.mapSeed() != null && !randomCorridors)
            throw new IllegalArgumentException("A map seed can only be used with Random Corridors");
        if (randomCorridors && configuration.mapSeed() == null)
            configuration = configuration.withMapSeed(java.util.concurrent.ThreadLocalRandom.current().nextLong());
        se.segersten.wreckage.game.domain.MapDefinition selected = configuration.mapId() == null || randomCorridors
                ? mapRepository.defaultFor(configuration.maxPlayers()) : mapRepository.get(configuration.mapId());
        if (!randomCorridors && !selected.players().supports(configuration.maxPlayers()))
            throw new IllegalArgumentException("Map '" + selected.id() + "' does not support the configured capacity");
        Board board = gameBoardFactory == null ? selected.toBoard() : gameBoardFactory.createBoard(selected);
        Instant createdAt = clock.instant();
        String hostToken = UUID.randomUUID().toString() + UUID.randomUUID();
        Game game = gameRepository.save(new Game(UUID.randomUUID(), List.of(), board,
                GameStatus.WAITING_FOR_PLAYERS, Map.of(), null, configuration, createdAt,
                createdAt.plusSeconds(configuration.joinTimeoutSeconds()), hash(hostToken)));
        return new HostedGame(game, hostToken);
    }

    public PlayerJoin addPlayer(UUID gameId, String name) {
        return addPlayer(gameId, name, null);
    }

    public PlayerJoin addPlayer(UUID gameId, String name, String hostToken) {
        Game game = findGameForUpdate(gameId);
        boolean joiningAsHost = hostToken != null;
        if (joiningAsHost) {
            authenticateHost(game, hostToken);
            if (game.getHostPlayerId() != null)
                throw new IllegalStateException("The game already has a host player");
        }
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        Instant now = clock.instant();
        Player player = game.addPlayer(name, hash(token), now);
        if (joiningAsHost) game.assignHost(player.getId());
        gameRepository.save(game);
        return new PlayerJoin(player, token);
    }

    public Player addBot(UUID gameId, UUID requestingPlayerId, String token) {
        Game game = authenticatedGameForUpdate(gameId, requestingPlayerId, token);
        if (!requestingPlayerId.equals(game.getHostPlayerId()))
            throw new SecurityException("Only the host can add bots");
        int nameIndex = 1;
        String name;
        do {
            name = "Bot " + nameIndex++;
        } while (hasPlayerNamed(game, name));
        Player bot = game.addAutomatedPlayer(name, hash(UUID.randomUUID().toString()), clock.instant());
        gameRepository.save(game);
        return bot;
    }

    public Game startGame(UUID gameId, String hostToken) {
        Game game = findGameForUpdate(gameId);
        authenticateHost(game, hostToken);
        if (game.getStatus() != GameStatus.WAITING_FOR_PLAYERS || game.getRound() != null)
            throw new IllegalStateException("The lobby is closed");
        if (game.getPlayers().size() < 2) throw new IllegalStateException("At least two players are required");
        boolean randomCorridors = GameConfiguration.RANDOM_CORRIDORS_MAP_ID.equals(game.getConfiguration().mapId());
        var selected = randomCorridors
                ? playableMapGenerator.generate(game.getConfiguration().mapSeed(), game.getPlayers().size(),
                        CorridorGenerationParameters.defaults())
                : game.getConfiguration().mapId() == null
                        ? mapRepository.defaultFor(game.getPlayers().size())
                        : mapRepository.get(game.getConfiguration().mapId());
        if (randomCorridors) LOGGER.info("Generated Random Corridors map:{}{}",
                System.lineSeparator(), corridorAsciiFormatter.format(selected, game.getConfiguration().mapSeed()));
        Board selectedBoard = gameBoardFactory == null ? selected.toBoard()
                : gameBoardFactory.createBoard(selected, game.getPlayers().size());
        var effective = new se.segersten.wreckage.game.domain.MapDefinition(selectedBoard.mapId(), selectedBoard.mapName(),
                selectedBoard.width(), selectedBoard.height(),
                new se.segersten.wreckage.game.domain.PlayerCountRange(game.getPlayers().size(), game.getPlayers().size()),
                selectedBoard.spawnPoints(), selectedBoard.orderedCheckpoints(), selectedBoard.obstacles().stream().toList(),
                selectedBoard.walls().stream().toList(), selectedBoard.pits().stream().toList(),
                selectedBoard.conveyors(), selectedBoard.rotators(), selectedBoard.controlPoints().stream().toList());
        game.start(clock.instant(), effective, dealHands(game));
        playerAutomation.lockBotPrograms(game);
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
        Round round = game.startRound(clock.instant(), dealHands(game));
        playerAutomation.lockBotPrograms(game);
        resolveIfReady(game);
        gameRepository.save(game);
        return round;
    }

    public void completeExpiredPlanning(){Instant now=clock.instant();for(Game game:gameRepository.findAllByStatusForUpdate(GameStatus.RUNNING)){Round round=game.getRound();if(round!=null&&round.completeTimedOutPrograms(now)){consumeSelectedShields(game);resolveIfReady(game);gameRepository.save(game);}}}

    public Game getPlayerGame(UUID gameId, UUID playerId, String token) {
        return authenticatedGame(gameId, playerId, token);
    }

    public Round saveProgramDraft(UUID gameId, UUID playerId, String token, List<MovementOrder> orders) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        boolean shield = game.getRound() != null && game.getRound().programs().get(playerId).shieldSelected();
        return saveProgramDraft(game, playerId, orders, shield);
    }

    public Round saveProgramDraft(UUID gameId, UUID playerId, String token, List<MovementOrder> orders,
                                  boolean shieldSelected) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        return saveProgramDraft(game, playerId, orders, shieldSelected);
    }

    private Round saveProgramDraft(Game game, UUID playerId, List<MovementOrder> orders, boolean shieldSelected) {
        if (game.getRound() == null) throw new IllegalStateException("No round has started");
        validateShield(game, playerId, shieldSelected);
        game.getRound().reorder(playerId, orders, shieldSelected);
        gameRepository.save(game);
        return game.getRound();
    }

    public Round submitProgram(UUID gameId, UUID playerId, String token, List<MovementOrder> orders) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        boolean shield = game.getRound() != null && game.getRound().programs().get(playerId).shieldSelected();
        return submitProgram(game, playerId, orders, shield);
    }

    public Round submitProgram(UUID gameId, UUID playerId, String token, List<MovementOrder> orders,
                               boolean shieldSelected) {
        Game game = authenticatedGameForUpdate(gameId, playerId, token);
        return submitProgram(game, playerId, orders, shieldSelected);
    }

    private Round submitProgram(Game game, UUID playerId, List<MovementOrder> orders, boolean shieldSelected) {
        if (game.getRound() == null) throw new IllegalStateException("No round has started");
        validateShield(game, playerId, shieldSelected);
        game.getRound().lock(playerId, orders, shieldSelected);
        if (shieldSelected) game.consumeShield(playerId);
        resolveIfReady(game);
        gameRepository.save(game);
        return game.getRound();
    }

    private void validateShield(Game game, UUID playerId, boolean selected) {
        if (selected && !game.isShieldAvailable(playerId))
            throw new IllegalStateException("Shield has already been consumed");
    }

    private void consumeSelectedShields(Game game) {
        game.getRound().programs().values().stream().filter(program -> program.ready() && program.shieldSelected())
                .forEach(program -> {
                    if (game.isShieldAvailable(program.playerId())) game.consumeShield(program.playerId());
                });
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

    private static boolean hasPlayerNamed(Game game, String name) {
        return game.getPlayers().stream().anyMatch(player -> player.getName().equals(name));
    }

    public record PlayerJoin(Player player, String token) {}
    public record HostedGame(Game game, String hostToken) {}

    private static MapRepository localMaps() {
        return new ResourceMapRepository(new MapValidator());
    }

    private Map<UUID, List<MovementOrder>> dealHands(Game game) {
        return game.getPlayers().stream().collect(java.util.stream.Collectors.toMap(
                Player::getId, ignored -> cardDealer.deal(), (left, right) -> left, java.util.LinkedHashMap::new));
    }

    private static ProgrammingCardDealer defaultDealer() {
        var weights = new java.util.LinkedHashMap<MovementOrder, Integer>();
        weights.put(MovementOrder.FORWARD_1, 20); weights.put(MovementOrder.FORWARD_2, 15);
        weights.put(MovementOrder.FORWARD_3, 10); weights.put(MovementOrder.REVERSE_1, 10);
        weights.put(MovementOrder.TURN_LEFT, 15); weights.put(MovementOrder.TURN_RIGHT, 15);
        weights.put(MovementOrder.U_TURN, 5); weights.put(MovementOrder.LASER, 10);
        return new ProgrammingCardDealer(weights, java.util.random.RandomGenerator.getDefault());
    }
}
