package se.segersten.wreckage.game.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;

public class Game {
    private final UUID id;
    private final List<Player> players;
    private Board board;
    private GameStatus status;
    private GameConfiguration configuration;
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
        int index = players.size();
        if (index >= board.spawnPoints().size()) throw new IllegalStateException("The map has no spawn point for this player");
        Player player = Player.create(UUID.randomUUID(), nickname, tokenHash);
        players.add(player);
        SpawnPoint spawn = board.spawnPoints().get(index);
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), player.getId(), spawn.position(), spawn.orientation());
        vehicles.put(player.getId(), new VehicleState(vehicle, spawn.position(), spawn.orientation()));
        return player;
    }

    public boolean startIfReady(Instant now) {
        Objects.requireNonNull(now);
        if (status != GameStatus.WAITING_FOR_PLAYERS || round != null || players.size() < 2)
            return false;
        boolean full = players.size() >= configuration.maxPlayers();
        boolean expired = !now.isBefore(joinDeadline);
        if (!full && !expired) return false;
        MatchSettings settings = MatchSettings.forPlayerCount(players.size());
        board = board.withDimensions(settings.boardWidth(), settings.boardHeight());
        configuration = configuration.withRoundLimit(settings.roundLimit());
        startRound(now);
        return true;
    }

    public Round startRound(Instant now) {
        if (status == GameStatus.FINISHED) throw new IllegalStateException("The game is finished");
        if (round != null && round.number() >= configuration.roundLimit())
            throw new IllegalStateException("The round limit has been reached");
        if (players.isEmpty()) throw new IllegalStateException("A round needs at least one player");
        if (round != null && round.phase() != RoundPhase.PLAYBACK)
            throw new IllegalStateException("The current round is not finished");
        if (round != null) {
            round.finalVehicleStates().forEach(s -> vehicles.put(s.vehicle().playerId(), s));
        }
        List<RoundEvent> startEvents = respawnCrashedVehicles();
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        for (Player player : players) {
            VehicleState vehicle = vehicles.get(player.getId());
            if (vehicle == null || !vehicle.isActive()) continue;
            programs.put(player.getId(),PlayerProgram.empty(player.getId(),configuration.programSize()));
        }
        List<UUID> initiative = nextInitiative(programs);
        round = new Round(round == null ? 1 : round.number() + 1,RoundPhase.PLANNING, programs, initiative,
                new GameState(board, getVehicleStates().stream()
                        .filter(VehicleState::isActive)
                        .toList()),List.of(),now.plusSeconds(configuration.planningTimeoutSeconds()),
                players.stream().collect(java.util.stream.Collectors.toMap(Player::getId, Player::getScore)), startEvents);
        status = GameStatus.RUNNING;
        if (programs.isEmpty()) {
            round = new Round(round.number(), RoundPhase.PLAYBACK, programs, initiative,
                    round.initialState(), List.of(),round.planningDeadline(), round.initialScores(), round.startEvents());
            completeRound();
        }
        return round;
    }

    private List<RoundEvent> respawnCrashedVehicles() {
        List<RoundEvent> events = new ArrayList<>();
        java.util.Set<Position> occupied = vehicles.values().stream().filter(VehicleState::isActive)
                .map(VehicleState::position).collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
        for (Player player : players) {
            VehicleState crashed = vehicles.get(player.getId());
            if (crashed == null || crashed.isActive()) continue;
            Vehicle vehicle = crashed.vehicle();
            int own = java.util.stream.IntStream.range(0, board.spawnPoints().size())
                    .filter(i -> board.spawnPoints().get(i).position().equals(vehicle.spawnPoint()))
                    .findFirst().orElse(0);
            SpawnPoint selected = null;
            for (int offset = 0; offset < board.spawnPoints().size(); offset++) {
                SpawnPoint candidate = board.spawnPoints().get((own + offset) % board.spawnPoints().size());
                if (!occupied.contains(candidate.position())) { selected = candidate; break; }
            }
            if (selected == null) continue;
            VehicleState respawned = new VehicleState(vehicle, selected.position(), vehicle.spawnOrientation(),
                    VehicleStatus.ACTIVE);
            vehicles.put(player.getId(), respawned);
            occupied.add(selected.position());
            events.add(RoundEvent.vehicleRespawned(crashed, respawned).withSequence(events.size() + 1));
        }
        return List.copyOf(events);
    }

    private List<UUID> nextInitiative(Map<UUID, PlayerProgram> programs) {
        if (round == null) {
            return players.stream().map(Player::getId).filter(programs::containsKey).toList();
        }
        List<UUID> rotated = new ArrayList<>(round.initiative());
        if (!rotated.isEmpty()) rotated.add(rotated.remove(0));
        rotated.removeIf(playerId -> !programs.containsKey(playerId));
        players.stream().map(Player::getId)
                .filter(programs::containsKey).filter(playerId -> !rotated.contains(playerId))
                .forEach(rotated::add);
        return List.copyOf(rotated);
    }

    public void completeRound() {
        if (round == null || round.phase() != RoundPhase.PLAYBACK) return;
        round.finalVehicleStates().forEach(s -> vehicles.put(s.vehicle().playerId(), s));
        if (round.number() >= configuration.roundLimit())
            status = GameStatus.FINISHED;
    }

    public List<GamePlacement> getPlacements() {
        if (status != GameStatus.FINISHED) return List.of();
        var sorted = players.stream().sorted(java.util.Comparator.comparingInt(Player::getScore).reversed()).toList();
        List<GamePlacement> result = new ArrayList<>();
        Player previous = null;
        int placement = 0;
        for (int index = 0; index < sorted.size(); index++) {
            Player player = sorted.get(index);
            if (previous == null || player.getScore() != previous.getScore()) placement = index + 1;
            result.add(new GamePlacement(player.getId(), placement, player.getScore(),
                    player.getVisitedCheckpoints().size(), player.getCrashes(), placement == 1));
            previous = player;
        }
        return List.copyOf(result);
    }

    public Player requirePlayer(UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not part of this game"));
    }
}
