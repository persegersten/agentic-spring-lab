package se.segersten.wreckage.game.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class Game {
    private final UUID id;
    private final List<Player> players;
    private final Board board;
    private final GameStatus status;
    private final Map<UUID, VehicleState> vehicles;
    private Round round;

    public Game(UUID id, Board board) { this(id, new ArrayList<>(), board, GameStatus.RUNNING); }
    public Game(UUID id, List<Player> players, Board board) { this(id, players, board, GameStatus.RUNNING); }
    public Game(UUID id, List<Player> players, Board board, GameStatus status) {
        this(id, players, board, status, Map.of(), null);
    }
    public Game(UUID id, List<Player> players, Board board, GameStatus status,
                Map<UUID, VehicleState> vehicles, Round round) {
        this.id = Objects.requireNonNull(id); this.players = new ArrayList<>(Objects.requireNonNull(players));
        this.board = Objects.requireNonNull(board); this.status = Objects.requireNonNull(status);
        this.vehicles = new LinkedHashMap<>(vehicles); this.round = round;
    }
    public UUID getId() { return id; }
    public List<Player> getPlayers() { return List.copyOf(players); }
    public Board getBoard() { return board; }
    public GameStatus getStatus() { return status; }
    public List<VehicleState> getVehicleStates() { return List.copyOf(vehicles.values()); }
    public Round getRound() { return round; }

    public Player addPlayer(String name) { return addPlayer(name, "legacy"); }
    public Player addPlayer(String name, String tokenHash) {
        if (round != null) throw new IllegalStateException("Cannot add players after rounds have started");
        Player player = Player.create(UUID.randomUUID(), name, tokenHash);
        players.add(player);
        int index = players.size() - 1;
        Position position = new Position(index % board.width(), (index / board.width()) % board.height());
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), player.getId());
        vehicles.put(player.getId(), new VehicleState(vehicle, position, Direction.SOUTH));
        return player;
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
        return round;
    }

    public Player requirePlayer(UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not part of this game"));
    }
}
