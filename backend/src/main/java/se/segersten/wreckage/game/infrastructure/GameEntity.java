package se.segersten.wreckage.game.infrastructure;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.*;
import se.segersten.wreckage.game.domain.*;

@Entity @Table(name = "game")
class GameEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "domain_id", nullable = false, unique = true) private UUID domainId;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "join_deadline", nullable = false) private Instant joinDeadline;
    @Column(name = "host_token_hash", nullable = false) private String hostTokenHash;
    @Column(name = "max_players", nullable = false) private Integer maxPlayers;
    @Column(name = "join_timeout_seconds", nullable = false) private Integer joinTimeoutSeconds;
    @Column(name = "program_size", nullable = false) private Integer programSize;
    @Column(name = "planning_timeout_seconds", nullable = false) private Integer planningTimeoutSeconds;
    @Column(name = "round_limit", nullable = false) private Integer roundLimit;
    @Column(name = "checkpoint_score", nullable = false) private Integer checkpointScore;
    @Column(name = "control_point_score", nullable = false) private Integer controlPointScore;
    @Column(name = "crash_penalty", nullable = false) private Integer crashPenalty;
    @Column(name = "push_crash_score", nullable = false) private Integer pushCrashScore;
    @Column(name = "weapon_crash_score", nullable = false) private Integer weaponCrashScore;
    @Column(name = "board_width") private Integer boardWidth;
    @Column(name = "board_height") private Integer boardHeight;
    @Column(name = "board_edge_walls", nullable = false) private String boardWalls;
    @Column(name = "board_pits", nullable = false) private String boardPits;
    @Column(name = "board_checkpoints", nullable = false) private String boardCheckpoints;
    @Column(name = "board_control_points", nullable = false) private String boardControlPoints;
    @Column(name = "board_spawn_points", nullable = false) private String boardSpawnPoints;
    @Column(name="board_conveyors",nullable=false)private String boardConveyors;@Column(name="board_rotators",nullable=false)private String boardRotators;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private GameStatus status;
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<PlayerEntity> players = new ArrayList<>();
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) private List<VehicleEntity> vehicles = new ArrayList<>();
    @OneToOne(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) private RoundEntity round;
    protected GameEntity() {}
    private GameEntity(UUID domainId, Board board) { this.domainId = domainId; setBoard(board); }
    static GameEntity fromDomain(Game game) { return new GameEntity(game.getId(), game.getBoard()).updateFrom(game); }
    GameEntity updateFrom(Game game) {
        setBoard(game.getBoard()); status = game.getStatus();
        GameConfiguration configuration = game.getConfiguration();
        maxPlayers = configuration.maxPlayers(); joinTimeoutSeconds = configuration.joinTimeoutSeconds();
        programSize = configuration.programSize(); planningTimeoutSeconds = configuration.planningTimeoutSeconds();
        roundLimit = configuration.roundLimit(); checkpointScore = configuration.checkpointScore();
        controlPointScore = configuration.controlPointScore();
        crashPenalty = configuration.crashPenalty(); pushCrashScore = configuration.pushCrashScore();
        weaponCrashScore = configuration.weaponCrashScore();
        joinDeadline = game.getJoinDeadline(); hostTokenHash = game.getHostTokenHash(); syncPlayers(game.getPlayers()); syncVehicles(game.getVehicleStates());
        if (game.getRound() != null) round = round == null ? RoundEntity.fromDomain(game.getRound(), this) : round.updateFrom(game.getRound());
        return this;
    }
    private void setBoard(Board board) {
        boardWidth = board.width(); boardHeight = board.height();
        boardWalls = board.walls().stream().sorted(java.util.Comparator
                        .comparingInt((Wall wall) -> wall.cell().x())
                        .thenComparingInt(wall -> wall.cell().y())
                        .thenComparing(Wall::direction))
                .map(wall -> wall.cell().x() + "," + wall.cell().y() + "," + wall.direction())
                .collect(Collectors.joining("|"));
        boardConveyors=board.conveyors().stream().map(c->c.position().x()+","+c.position().y()+","+c.direction()).collect(Collectors.joining("|"));boardRotators=board.rotators().stream().map(r->r.position().x()+","+r.position().y()+","+r.rotation()).collect(Collectors.joining("|"));
        boardPits = board.pits().stream().sorted(java.util.Comparator.comparingInt(Position::x).thenComparingInt(Position::y))
                .map(position -> position.x() + "," + position.y()).collect(Collectors.joining("|"));
        boardCheckpoints = board.checkpoints().stream().sorted(java.util.Comparator.comparing(Checkpoint::id))
                .map(checkpoint -> checkpoint.id() + "," + checkpoint.position().x() + "," + checkpoint.position().y())
                .collect(Collectors.joining("|"));
        boardControlPoints = board.controlPoints().stream()
                .sorted(java.util.Comparator.comparingInt(Position::x).thenComparingInt(Position::y))
                .map(position -> position.x() + "," + position.y()).collect(Collectors.joining("|"));
        boardSpawnPoints = board.spawnPoints().stream()
                .map(spawn -> spawn.position().x() + "," + spawn.position().y() + "," + spawn.orientation())
                .collect(Collectors.joining("|"));
    }
    private void syncPlayers(List<Player> domainPlayers) {
        for (Player player : domainPlayers) {
            PlayerEntity entity = players.stream().filter(p -> p.getDomainId().equals(player.getId())).findFirst().orElse(null);
            if (entity == null) players.add(PlayerEntity.fromDomain(player, this)); else entity.updateFrom(player);
        }
    }
    private void syncVehicles(List<VehicleState> states) {
        for (VehicleState state : states) {
            VehicleEntity entity = vehicles.stream().filter(v -> v.domainId().equals(state.vehicle().id())).findFirst().orElse(null);
            if (entity == null) vehicles.add(VehicleEntity.fromDomain(state, this)); else entity.updateFrom(state);
        }
    }
    Game toDomain() {
        java.util.Set<Wall> walls = boardWalls == null || boardWalls.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(boardWalls.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new Wall(new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])),
                        Direction.valueOf(parts[2])))
                .collect(Collectors.toUnmodifiableSet());
        java.util.Set<Position> pits = boardPits == null || boardPits.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(boardPits.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                .collect(Collectors.toUnmodifiableSet());
        java.util.Set<Checkpoint> checkpoints = boardCheckpoints == null || boardCheckpoints.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(boardCheckpoints.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new Checkpoint(parts[0], new Position(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))))
                .collect(Collectors.toUnmodifiableSet());
        java.util.Set<Position> controlPoints = boardControlPoints == null || boardControlPoints.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(boardControlPoints.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                .collect(Collectors.toUnmodifiableSet());
        List<SpawnPoint> spawnPoints = boardSpawnPoints == null || boardSpawnPoints.isBlank()
                ? new Board(boardWidth, boardHeight).spawnPoints()
                : java.util.Arrays.stream(boardSpawnPoints.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new SpawnPoint(new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])),
                        Direction.valueOf(parts[2]))).toList();
        List<Conveyor> conveyors=boardConveyors==null||boardConveyors.isBlank()?List.of():java.util.Arrays.stream(boardConveyors.split("\\|")).map(v->v.split(",")).map(p->new Conveyor(new Position(Integer.parseInt(p[0]),Integer.parseInt(p[1])),Direction.valueOf(p[2]))).toList();List<Rotator> rotators=boardRotators==null||boardRotators.isBlank()?List.of():java.util.Arrays.stream(boardRotators.split("\\|")).map(v->v.split(",")).map(p->new Rotator(new Position(Integer.parseInt(p[0]),Integer.parseInt(p[1])),Rotation.valueOf(p[2]))).toList();
        Board board=new Board(boardWidth,boardHeight,walls,pits,checkpoints,spawnPoints,conveyors,rotators,controlPoints);
        List<Player> domainPlayers = players.stream().map(PlayerEntity::toDomain).toList();
        var vehicleMap = new LinkedHashMap<UUID, VehicleState>();
        var byVehicleId = new LinkedHashMap<UUID, Vehicle>();
        for (VehicleEntity entity : vehicles) { VehicleState state = entity.toDomain(); vehicleMap.put(state.vehicle().playerId(), state); byVehicleId.put(state.vehicle().id(), state.vehicle()); }
        Round domainRound = round == null ? null : round.toDomain(board, byVehicleId);
        GameConfiguration configuration = new GameConfiguration(maxPlayers, joinTimeoutSeconds,
                programSize, planningTimeoutSeconds, roundLimit, checkpointScore, controlPointScore,
                crashPenalty, pushCrashScore, weaponCrashScore);
        return new Game(domainId, domainPlayers, board, status, vehicleMap, domainRound,
                configuration, createdAt.toInstant(), joinDeadline, hostTokenHash);
    }
}
