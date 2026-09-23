package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public record RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                         UUID sourcePlayerId, UUID sourceVehicleId,
                         Position oldPosition, Position newPosition,
                         Direction oldDirection, Direction newDirection,
                         int oldDamage, int newDamage) {
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, playerId, vehicleId, oldPosition, newPosition,
                oldDirection, newDirection, 0, 0);
    }

    public RoundEvent {
        Objects.requireNonNull(type); Objects.requireNonNull(playerId); Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(sourcePlayerId); Objects.requireNonNull(sourceVehicleId);
        Objects.requireNonNull(oldPosition); Objects.requireNonNull(newPosition);
        Objects.requireNonNull(oldDirection); Objects.requireNonNull(newDirection);
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        if (oldDamage < 0 || newDamage < 0) throw new IllegalArgumentException("damage must not be negative");
    }
    public RoundEvent withSequence(int value) {
        return new RoundEvent(value, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, oldDamage, newDamage);
    }
}
