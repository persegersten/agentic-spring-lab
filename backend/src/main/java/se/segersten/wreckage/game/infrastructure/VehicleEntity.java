package se.segersten.wreckage.game.infrastructure;

import java.util.UUID;
import jakarta.persistence.*;
import se.segersten.wreckage.game.domain.*;

@Entity @Table(name = "vehicle")
class VehicleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="domain_id", nullable=false, unique=true) private UUID domainId;
    @Column(name="player_domain_id", nullable=false, unique=true) private UUID playerDomainId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="game_id", nullable=false) private GameEntity game;
    @Column(name="position_x", nullable=false) private Double x;
    @Column(name="position_y", nullable=false) private Double y;
    @Enumerated(EnumType.STRING) @Column(name="direction", nullable=false) private Direction direction;
    @Enumerated(EnumType.STRING) @Column(name="status", nullable=false) private VehicleStatus status;
    @Column(name="spawn_x", nullable=false) private Integer spawnX;
    @Column(name="spawn_y", nullable=false) private Integer spawnY;
    @Enumerated(EnumType.STRING) @Column(name="spawn_direction", nullable=false) private Direction spawnDirection;
    protected VehicleEntity() {}
    static VehicleEntity fromDomain(VehicleState state, GameEntity game) { var e = new VehicleEntity(); e.domainId=state.vehicle().id(); e.playerDomainId=state.vehicle().playerId(); e.game=game; return e.updateFrom(state); }
    VehicleEntity updateFrom(VehicleState state) { x=(double)state.position().x(); y=(double)state.position().y(); direction=state.orientation(); status=state.status(); spawnX=state.vehicle().spawnPoint().x(); spawnY=state.vehicle().spawnPoint().y(); spawnDirection=state.vehicle().spawnOrientation(); return this; }
    UUID domainId() { return domainId; }
    VehicleState toDomain() { return new VehicleState(new Vehicle(domainId, playerDomainId,
            new Position(spawnX, spawnY), spawnDirection), new Position(x.intValue(), y.intValue()), direction, status); }
}
