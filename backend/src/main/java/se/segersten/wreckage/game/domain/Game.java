package se.segersten.wreckage.game.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.util.function.Supplier;

public class Game {
    private final UUID id;
    private final List<Player> players;
    private final Board board;
    private GameStatus status;
    private final GameConfiguration configuration;
    private final Instant createdAt;
    private final Instant joinDeadline;
    private final Map<UUID, VehicleState> vehicles;
    private Round round;

    public Game(UUID id, Board board) { this(id, new ArrayList<>(), board, GameStatus.RUNNING); }
    public Game(UUID id, List<Player> players, Board board) { this(id, players, board, GameStatus.RUNNING); }
    public Game(UUID id, List<Player> players, Board board, GameStatus status) {
        this(id, players, board, status, Map.of(), null, GameConfiguration.defaults(),
                Instant.now(), Instant.now().plusSeconds(GameConfiguration.DEFAULT_JOIN_TIMEOUT_SECONDS));
    }
    public Game(UUID id, List<Player> players, Board board, GameStatus status,
                Map<UUID, VehicleState> vehicles, Round round) {
        this(id, players, board, status, vehicles, round, GameConfiguration.defaults(),
                Instant.now(), Instant.now().plusSeconds(GameConfiguration.DEFAULT_JOIN_TIMEOUT_SECONDS));
    }
    public Game(UUID id, List<Player> players, Board board, GameStatus status,
                Map<UUID, VehicleState> vehicles, Round round, GameConfiguration configuration,
                Instant createdAt, Instant joinDeadline) {
        this.id = Objects.requireNonNull(id); this.players = new ArrayList<>(Objects.requireNonNull(players));
        this.board = Objects.requireNonNull(board); this.status = Objects.requireNonNull(status);
        this.configuration = Objects.requireNonNull(configuration);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.joinDeadline = Objects.requireNonNull(joinDeadline);
        this.vehicles = new LinkedHashMap<>(vehicles); this.round = round;
    }
    public UUID getId() { return id; }
    public List<Player> getPlayers() { return List.copyOf(players); }
    public Board getBoard() { return board; }
    public GameStatus getStatus() { return status; }
    public GameConfiguration getConfiguration() { return configuration; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getJoinDeadline() { return joinDeadline; }
    public List<VehicleState> getVehicleStates() { return List.copyOf(vehicles.values()); }
    public Round getRound() { return round; }

    public Player addPlayer(String name) { return addPlayer(name, "legacy", Instant.now()); }
    public Player addPlayer(String name, String tokenHash) { return addPlayer(name, tokenHash, Instant.now()); }
    public Player addPlayer(String name, String tokenHash, Instant now) {
        if (round != null) throw new IllegalStateException("The lobby is closed");
        if (!now.isBefore(joinDeadline)) throw new IllegalStateException("The lobby is closed");
        if (players.size() >= configuration.maxPlayers()) throw new IllegalStateException("The lobby is full");
        String nickname = name == null ? null : name.trim();
        if (nickname != null && players.stream().anyMatch(player -> player.getName().equals(nickname)))
            throw new IllegalArgumentException("Nickname is already in use");
        Player player = Player.create(UUID.randomUUID(), nickname, tokenHash);
        players.add(player);
        int index = players.size() - 1;
        Position position = new Position(index % board.width(), (index / board.width()) % board.height());
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), player.getId());
        vehicles.put(player.getId(), new VehicleState(vehicle, position, Direction.SOUTH));
        return player;
    }

    public boolean startIfReady(Instant now, Supplier<MovementOrder> cards) {
        Objects.requireNonNull(now);
        Objects.requireNonNull(cards);
        if (status != GameStatus.WAITING_FOR_PLAYERS || round != null || players.size() < 2)
            return false;
        boolean full = players.size() >= configuration.maxPlayers();
        boolean expired = !now.isBefore(joinDeadline);
        if (!full && !expired) return false;
        startRound(cards);
        return true;
    }

    public Round startRound(Supplier<MovementOrder> cards) {
        if (players.isEmpty()) throw new IllegalStateException("A round needs at least one player");
        if (round != null && round.phase() != RoundPhase.PLAYBACK)
            throw new IllegalStateException("The current round is not finished");
        if (round != null) {
            round.playback().getLast().vehicleStates().forEach(s -> vehicles.put(s.vehicle().playerId(), s));
        }
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        for (Player player : players) {
            programs.put(player.getId(), new PlayerProgram(player.getId(),
                    List.of(cards.get(), cards.get(), cards.get()), List.of()));
        }
        round = new Round(round == null ? 1 : round.number() + 1, programs,
                new GameState(board, getVehicleStates()));
        status = GameStatus.RUNNING;
        return round;
    }

    public Player requirePlayer(UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not part of this game"));
    }
}
