package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public record RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                         UUID sourcePlayerId, UUID sourceVehicleId,
                         Position oldPosition, Position newPosition,
                         Direction oldDirection, Direction newDirection,
                         Integer oldDamage, Integer newDamage, Integer damageDelta,
                         Integer oldScore, Integer newScore, Integer scoreDelta,
                         ScoreChangeReason scoreReason, String checkpointId) {
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, playerId, vehicleId, oldPosition, newPosition,
                oldDirection, newDirection, null, null, null, null, null, null, null, null);
    }
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      UUID sourcePlayerId, UUID sourceVehicleId, Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId, oldPosition, newPosition,
                oldDirection, newDirection, null, null, null, null, null, null, null, null);
    }

    public RoundEvent {
        Objects.requireNonNull(type); Objects.requireNonNull(playerId); Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(sourcePlayerId); Objects.requireNonNull(sourceVehicleId);
        Objects.requireNonNull(oldPosition); Objects.requireNonNull(newPosition);
        Objects.requireNonNull(oldDirection); Objects.requireNonNull(newDirection);
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        if (type == RoundEventType.SCORE_CHANGED
                && (oldScore == null || newScore == null || scoreDelta == null || scoreReason == null))
            throw new IllegalArgumentException("Score events require score details");
        if (type == RoundEventType.DAMAGE_APPLIED
                && (oldDamage == null || newDamage == null || damageDelta == null))
            throw new IllegalArgumentException("Damage events require damage details");
    }
    public RoundEvent withSequence(int value) {
        return new RoundEvent(value, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, oldDamage, newDamage, damageDelta,
                oldScore, newScore, scoreDelta, scoreReason, checkpointId);
    }

    public static RoundEvent scoreChanged(UUID playerId, UUID vehicleId, UUID sourcePlayerId,
                                          UUID sourceVehicleId, Position position, Direction direction,
                                          int oldScore, int newScore, ScoreChangeReason reason, String checkpointId) {
        return new RoundEvent(0, RoundEventType.SCORE_CHANGED, playerId, vehicleId, sourcePlayerId,
                sourceVehicleId, position, position, direction, direction, null, null, null,
                oldScore, newScore, newScore - oldScore, reason, checkpointId);
    }

    public static RoundEvent vehicleRespawned(VehicleState oldState, VehicleState newState) {
        return new RoundEvent(0, RoundEventType.VEHICLE_RESPAWNED, newState.vehicle().playerId(),
                newState.vehicle().id(), newState.vehicle().playerId(), newState.vehicle().id(),
                oldState.position(), newState.position(), oldState.orientation(), newState.orientation(),
                oldState.damage(), newState.damage(), newState.damage() - oldState.damage(),
                null, null, null, null, null);
    }
}
