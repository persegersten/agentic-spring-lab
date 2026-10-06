package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public final class Vehicle {
    private final UUID id;
    private final UUID playerId;
    private final Position spawnPoint;
    private final Direction spawnOrientation;
    private final PrimaryWeapon primaryWeapon;
    private final SpecialAbility specialAbility;

    public Vehicle() { this(UUID.randomUUID(), UUID.randomUUID(), new Position(0, 0), Direction.SOUTH); }
    public Vehicle(UUID id, UUID playerId) {
        this(id, playerId, new Position(0, 0), Direction.SOUTH);
    }
    public Vehicle(UUID id, UUID playerId, Position spawnPoint, Direction spawnOrientation) {
        this(id, playerId, spawnPoint, spawnOrientation, PrimaryWeapon.LASER, SpecialAbility.SHIELD);
    }
    public Vehicle(UUID id, UUID playerId, Position spawnPoint, Direction spawnOrientation,
                   PrimaryWeapon primaryWeapon, SpecialAbility specialAbility) {
        this.id = Objects.requireNonNull(id);
        this.playerId = Objects.requireNonNull(playerId);
        this.spawnPoint = Objects.requireNonNull(spawnPoint);
        this.spawnOrientation = Objects.requireNonNull(spawnOrientation);
        this.primaryWeapon = Objects.requireNonNull(primaryWeapon);
        this.specialAbility = Objects.requireNonNull(specialAbility);
    }
    public UUID id() { return id; }
    public UUID playerId() { return playerId; }
    public Position spawnPoint() { return spawnPoint; }
    public Direction spawnOrientation() { return spawnOrientation; }
    public PrimaryWeapon primaryWeapon() { return primaryWeapon; }
    public SpecialAbility specialAbility() { return specialAbility; }
    public boolean permits(ActionType action) { return primaryWeapon.permits(action) || specialAbility.permits(action); }
    public Vehicle withLoadout(PrimaryWeapon weapon, SpecialAbility ability) {
        return new Vehicle(id, playerId, spawnPoint, spawnOrientation, weapon, ability);
    }
}
