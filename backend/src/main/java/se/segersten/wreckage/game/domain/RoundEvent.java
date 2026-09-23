package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public record RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                         Position oldPosition, Position newPosition,
                         Direction oldDirection, Direction newDirection) {
    public RoundEvent {
        Objects.requireNonNull(type); Objects.requireNonNull(playerId); Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(oldPosition); Objects.requireNonNull(newPosition);
        Objects.requireNonNull(oldDirection); Objects.requireNonNull(newDirection);
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
    }
    public RoundEvent withSequence(int value) {
        return new RoundEvent(value, type, playerId, vehicleId, oldPosition, newPosition, oldDirection, newDirection);
    }
}
