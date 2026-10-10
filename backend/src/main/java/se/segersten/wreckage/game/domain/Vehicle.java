package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public final class Vehicle {
    private final UUID id;
    private final UUID playerId;
    private final Position spawnPoint;
    private final Direction spawnOrientation;

    public Vehicle() { this(UUID.randomUUID(), UUID.randomUUID(), new Position(0, 0), Direction.SOUTH); }
    public Vehicle(UUID id, UUID playerId) {
        this(id, playerId, new Position(0, 0), Direction.SOUTH);
    }
    public Vehicle(UUID id, UUID playerId, Position spawnPoint, Direction spawnOrientation) {
        this.id = Objects.requireNonNull(id);
        this.playerId = Objects.requireNonNull(playerId);
        this.spawnPoint = Objects.requireNonNull(spawnPoint);
        this.spawnOrientation = Objects.requireNonNull(spawnOrientation);
    }
    public UUID id() { return id; }
    public UUID playerId() { return playerId; }
    public Position spawnPoint() { return spawnPoint; }
    public Direction spawnOrientation() { return spawnOrientation; }
}
