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
    private final String hostTokenHash;
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
        this(id, players, board, status, vehicles, round, configuration, createdAt, joinDeadline, "legacy");
    }
    public Game(UUID id, List<Player> players, Board board, GameStatus status,
                Map<UUID, VehicleState> vehicles, Round round, GameConfiguration configuration,
                Instant createdAt, Instant joinDeadline, String hostTokenHash) {
        this.id = Objects.requireNonNull(id); this.players = new ArrayList<>(Objects.requireNonNull(players));
        this.board = Objects.requireNonNull(board); this.status = Objects.requireNonNull(status);
        this.configuration = Objects.requireNonNull(configuration);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.joinDeadline = Objects.requireNonNull(joinDeadline);
        this.hostTokenHash = Objects.requireNonNull(hostTokenHash);
        this.vehicles = new LinkedHashMap<>(vehicles); this.round = round;
    }
    public UUID getId() { return id; }
    public List<Player> getPlayers() { return List.copyOf(players); }
    public Board getBoard() { return board; }
    public GameStatus getStatus() { return status; }
    public GameConfiguration getConfiguration() { return configuration; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getJoinDeadline() { return joinDeadline; }
    public String getHostTokenHash() { return hostTokenHash; }
    public List<VehicleState> getVehicleStates() { return List.copyOf(vehicles.values()); }
    public Round getRound() { return round; }

    public Player addPlayer(String name) { return addPlayer(name, "legacy", Instant.now()); }
    public Player addPlayer(String name, String tokenHash) { return addPlayer(name, tokenHash, Instant.now()); }
    public Player addPlayer(String name, String tokenHash, Instant now) {
        return addPlayer(name, tokenHash, now, false);
    }
    public Player addAutomatedPlayer(String name, String tokenHash, Instant now) {
        return addPlayer(name, tokenHash, now, true);
    }
    private Player addPlayer(String name, String tokenHash, Instant now, boolean automated) {
        if (status == GameStatus.FINISHED || round != null)
            throw new IllegalStateException("The lobby is closed");
        if (players.size() >= configuration.maxPlayers()) throw new IllegalStateException("The lobby is full");
        String nickname = name == null ? null : name.trim();
        if (nickname != null && players.stream().anyMatch(player -> player.getName().equals(nickname)))
            throw new IllegalArgumentException("Nickname is already in use");
        int index = players.size();
        if (index >= board.spawnPoints().size()) throw new IllegalStateException("The map has no spawn point for this player");
        Player player = automated ? Player.createAutomated(UUID.randomUUID(), nickname, tokenHash)
                : Player.create(UUID.randomUUID(), nickname, tokenHash);
        players.add(player);
        SpawnPoint spawn = board.spawnPoints().get(index);
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), player.getId(), spawn.position(), spawn.orientation());
        vehicles.put(player.getId(), new VehicleState(vehicle, spawn.position(), spawn.orientation(),
                VehicleStatus.ACTIVE, 0, 0));
        return player;
    }

    public void updateLoadout(UUID playerId, PrimaryWeapon weapon, SpecialAbility ability) {
        Objects.requireNonNull(weapon, "weapon must not be null");
        Objects.requireNonNull(ability, "ability must not be null");
        requirePlayer(playerId);
        if (status != GameStatus.WAITING_FOR_PLAYERS || round != null)
            throw new IllegalStateException("The loadout is fixed after the match starts");
        VehicleState current = vehicles.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player has no vehicle");
        vehicles.put(playerId, new VehicleState(current.vehicle().withLoadout(weapon, ability), current.position(),
                current.orientation(), current.status(), current.damage(), 0));
    }

    public void start(Instant now) {
        start(now, null);
    }

    public void start(Instant now, MapDefinition selectedMap) {
        Objects.requireNonNull(now);
        if (status != GameStatus.WAITING_FOR_PLAYERS || round != null)
            throw new IllegalStateException("The lobby is closed");
        if (players.size() < 2) throw new IllegalStateException("At least two players are required");
        MatchSettings settings = MatchSettings.forPlayerCount(players.size());
        if (selectedMap != null) board = selectedMap.toBoard();
        else board = board.withDimensions(settings.boardWidth(), settings.boardHeight());
        if (board.width() != settings.boardWidth() || board.height() != settings.boardHeight())
            throw new IllegalArgumentException("Map dimensions do not match the player-count configuration");
        configuration = configuration.withRoundLimit(settings.roundLimit());
        List<SpawnPoint> selectedSpawns = balancedSpawns(board.spawnPoints(), players.size());
        for (int index = 0; index < players.size(); index++) {
            UUID playerId = players.get(index).getId(); VehicleState state = vehicles.get(playerId);
            SpawnPoint spawn = selectedSpawns.get(index);
            Vehicle old = state.vehicle();
            Vehicle vehicle = new Vehicle(old.id(), old.playerId(), spawn.position(), spawn.orientation(),
                    old.primaryWeapon(), old.specialAbility());
            vehicles.put(playerId, new VehicleState(vehicle, spawn.position(), spawn.orientation(),
                    VehicleStatus.ACTIVE, 0, old.primaryWeapon() == PrimaryWeapon.ROCKET ? 1 : 0));
        }
        startRound(now);
    }

    private List<SpawnPoint> balancedSpawns(List<SpawnPoint> starts, int count) {
        if (starts.size() < count) throw new IllegalStateException("The map has too few spawn points");
        if (starts.size() == count) return starts;
        List<SpawnPoint> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(starts.get(index * starts.size() / count));
        return List.copyOf(result);
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
                    VehicleStatus.ACTIVE, 0, crashed.rocketAmmo());
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
        if (players.stream().anyMatch(player -> player.hasCompletedCheckpoints(board))
                || round.number() >= configuration.roundLimit())
            status = GameStatus.FINISHED;
    }

    public List<GamePlacement> getPlacements() {
        if (status != GameStatus.FINISHED) return List.of();
        var sorted = players.stream().sorted(java.util.Comparator
                .comparingInt((Player player) -> player.getCapturedCheckpointCount(board)).reversed()
                .thenComparingInt(this::distanceToNextCheckpoint)).toList();
        List<GamePlacement> result = new ArrayList<>();
        Player previous = null;
        int placement = 0;
        for (int index = 0; index < sorted.size(); index++) {
            Player player = sorted.get(index);
            if (previous == null
                    || player.getCapturedCheckpointCount(board) != previous.getCapturedCheckpointCount(board)
                    || distanceToNextCheckpoint(player) != distanceToNextCheckpoint(previous)) placement = index + 1;
            result.add(new GamePlacement(player.getId(), placement, player.getScore(),
                    player.getCapturedCheckpointCount(board), nullableDistanceToNextCheckpoint(player),
                    player.getCrashes(), placement == 1));
            previous = player;
        }
        return List.copyOf(result);
    }

    private int distanceToNextCheckpoint(Player player) {
        Checkpoint next = player.getNextCheckpoint(board);
        VehicleState vehicle = vehicles.get(player.getId());
        if (next == null) return -1;
        if (vehicle == null) return Integer.MAX_VALUE;
        return Math.abs(vehicle.position().x() - next.position().x())
                + Math.abs(vehicle.position().y() - next.position().y());
    }

    private Integer nullableDistanceToNextCheckpoint(Player player) {
        return player.getNextCheckpoint(board) == null ? null : distanceToNextCheckpoint(player);
    }

    public Player requirePlayer(UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not part of this game"));
    }
}
