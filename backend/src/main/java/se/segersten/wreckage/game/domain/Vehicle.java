package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public final class Vehicle {
    private final UUID id;
    private final UUID playerId;

    public Vehicle() { this(UUID.randomUUID(), UUID.randomUUID()); }
    public Vehicle(UUID id, UUID playerId) {
        this.id = Objects.requireNonNull(id);
        this.playerId = Objects.requireNonNull(playerId);
    }
    public UUID id() { return id; }
    public UUID playerId() { return playerId; }
}
