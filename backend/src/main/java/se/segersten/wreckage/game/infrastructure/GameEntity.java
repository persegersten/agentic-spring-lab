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
    @Column(name = "max_players", nullable = false) private Integer maxPlayers;
    @Column(name = "join_timeout_seconds", nullable = false) private Integer joinTimeoutSeconds;
    @Column(name = "cards_per_round", nullable = false) private Integer cardsPerRound;
    @Column(name = "planning_timeout_seconds", nullable = false) private Integer planningTimeoutSeconds;
    @Column(name = "board_width") private Integer boardWidth;
    @Column(name = "board_height") private Integer boardHeight;
    @Column(name = "board_walls", nullable = false) private String boardWalls;
    @Column(name = "board_pits", nullable = false) private String boardPits;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private GameStatus status;
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) private List<PlayerEntity> players = new ArrayList<>();
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) private List<VehicleEntity> vehicles = new ArrayList<>();
    @OneToOne(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true) private RoundEntity round;
    protected GameEntity() {}
    private GameEntity(UUID domainId, Board board) { this.domainId = domainId; setBoard(board); }
    static GameEntity fromDomain(Game game) { return new GameEntity(game.getId(), game.getBoard()).updateFrom(game); }
    GameEntity updateFrom(Game game) {
        setBoard(game.getBoard()); status = game.getStatus();
        GameConfiguration configuration = game.getConfiguration();
        maxPlayers = configuration.maxPlayers(); joinTimeoutSeconds = configuration.joinTimeoutSeconds();
        cardsPerRound = configuration.cardsPerRound(); planningTimeoutSeconds = configuration.planningTimeoutSeconds();
        joinDeadline = game.getJoinDeadline(); addMissingPlayers(game.getPlayers()); syncVehicles(game.getVehicleStates());
        if (game.getRound() != null) round = round == null ? RoundEntity.fromDomain(game.getRound(), this) : round.updateFrom(game.getRound());
        return this;
    }
    private void setBoard(Board board) {
        boardWidth = board.width(); boardHeight = board.height();
        boardWalls = board.walls().stream().sorted(java.util.Comparator.comparingInt(Position::x).thenComparingInt(Position::y))
                .map(position -> position.x() + "," + position.y()).collect(Collectors.joining("|"));
        boardPits = board.pits().stream().sorted(java.util.Comparator.comparingInt(Position::x).thenComparingInt(Position::y))
                .map(position -> position.x() + "," + position.y()).collect(Collectors.joining("|"));
    }
    private void addMissingPlayers(List<Player> domainPlayers) {
        Set<UUID> ids = players.stream().map(PlayerEntity::getDomainId).collect(Collectors.toSet());
        domainPlayers.stream().filter(p -> !ids.contains(p.getId())).map(p -> PlayerEntity.fromDomain(p, this)).forEach(players::add);
    }
    private void syncVehicles(List<VehicleState> states) {
        for (VehicleState state : states) {
            VehicleEntity entity = vehicles.stream().filter(v -> v.domainId().equals(state.vehicle().id())).findFirst().orElse(null);
            if (entity == null) vehicles.add(VehicleEntity.fromDomain(state, this)); else entity.updateFrom(state);
        }
    }
    Game toDomain() {
        java.util.Set<Position> walls = boardWalls == null || boardWalls.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(boardWalls.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                .collect(Collectors.toUnmodifiableSet());
        java.util.Set<Position> pits = boardPits == null || boardPits.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(boardPits.split("\\|"))
                .map(value -> value.split(","))
                .map(parts -> new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                .collect(Collectors.toUnmodifiableSet());
        Board board = new Board(boardWidth, boardHeight, walls, pits);
        List<Player> domainPlayers = players.stream().map(PlayerEntity::toDomain).toList();
        var vehicleMap = new LinkedHashMap<UUID, VehicleState>();
        var byVehicleId = new LinkedHashMap<UUID, Vehicle>();
        for (VehicleEntity entity : vehicles) { VehicleState state = entity.toDomain(); vehicleMap.put(state.vehicle().playerId(), state); byVehicleId.put(state.vehicle().id(), state.vehicle()); }
        Round domainRound = round == null ? null : round.toDomain(board, byVehicleId);
        GameConfiguration configuration = new GameConfiguration(maxPlayers, joinTimeoutSeconds,
                cardsPerRound, planningTimeoutSeconds);
        return new Game(domainId, domainPlayers, board, status, vehicleMap, domainRound,
                configuration, createdAt.toInstant(), joinDeadline);
    }
}
