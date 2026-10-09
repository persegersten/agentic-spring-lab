package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public record RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                         UUID sourcePlayerId, UUID sourceVehicleId,
                         Position oldPosition, Position newPosition,
                         Direction oldDirection, Direction newDirection,
                         Integer oldDamage, Integer newDamage, Integer damageDelta,
                         Integer oldScore, Integer newScore, Integer scoreDelta,
                         ScoreChangeReason scoreReason, String checkpointId,
                         ActionType actionType, Integer oldAmmo, Integer newAmmo, Integer ammoDelta,
                         Integer registerIndex) {
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      UUID sourcePlayerId, UUID sourceVehicleId,
                      Position oldPosition, Position newPosition, Direction oldDirection, Direction newDirection,
                      Integer oldDamage, Integer newDamage, Integer damageDelta,
                      Integer oldScore, Integer newScore, Integer scoreDelta,
                      ScoreChangeReason scoreReason, String checkpointId, ActionType actionType,
                      Integer oldAmmo, Integer newAmmo, Integer ammoDelta) {
        this(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId, oldPosition, newPosition,
                oldDirection, newDirection, oldDamage, newDamage, damageDelta, oldScore, newScore, scoreDelta,
                scoreReason, checkpointId, actionType, oldAmmo, newAmmo, ammoDelta, null);
    }
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, playerId, vehicleId, oldPosition, newPosition,
                oldDirection, newDirection, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      UUID sourcePlayerId, UUID sourceVehicleId, Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId, oldPosition, newPosition,
                oldDirection, newDirection, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      UUID sourcePlayerId, UUID sourceVehicleId, Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection, Integer oldDamage, Integer newDamage,
                      Integer damageDelta, Integer oldScore, Integer newScore, Integer scoreDelta,
                      ScoreChangeReason scoreReason, String checkpointId) {
        this(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId, oldPosition, newPosition,
                oldDirection, newDirection, oldDamage, newDamage, damageDelta, oldScore, newScore, scoreDelta,
                scoreReason, checkpointId, null, null, null, null, null);
    }

    public RoundEvent {
        Objects.requireNonNull(type); Objects.requireNonNull(playerId); Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(sourcePlayerId); Objects.requireNonNull(sourceVehicleId);
        Objects.requireNonNull(oldPosition); Objects.requireNonNull(newPosition);
        Objects.requireNonNull(oldDirection); Objects.requireNonNull(newDirection);
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        if (registerIndex != null && registerIndex < 1) throw new IllegalArgumentException("registerIndex must be positive");
        if (type == RoundEventType.SCORE_CHANGED
                && (oldScore == null || newScore == null || scoreDelta == null || scoreReason == null))
            throw new IllegalArgumentException("Score events require score details");
        if ((type == RoundEventType.DAMAGE_APPLIED || type == RoundEventType.DAMAGE_PREVENTED)
                && (oldDamage == null || newDamage == null || damageDelta == null))
            throw new IllegalArgumentException("Damage events require damage details");
        if (type == RoundEventType.AMMO_CHANGED
                && (oldAmmo == null || newAmmo == null || ammoDelta == null))
            throw new IllegalArgumentException("Ammo events require ammo details");
    }
    public RoundEvent withSequence(int value) {
        return new RoundEvent(value, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, oldDamage, newDamage, damageDelta,
                oldScore, newScore, scoreDelta, scoreReason, checkpointId, actionType,
                oldAmmo, newAmmo, ammoDelta, registerIndex);
    }
    public RoundEvent withRegister(int value) {
        return new RoundEvent(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, oldDamage, newDamage, damageDelta,
                oldScore, newScore, scoreDelta, scoreReason, checkpointId, actionType,
                oldAmmo, newAmmo, ammoDelta, value);
    }

    public static RoundEvent scoreChanged(UUID playerId, UUID vehicleId, UUID sourcePlayerId,
                                          UUID sourceVehicleId, Position position, Direction direction,
                                          int oldScore, int newScore, ScoreChangeReason reason, String checkpointId) {
        return new RoundEvent(0, RoundEventType.SCORE_CHANGED, playerId, vehicleId, sourcePlayerId,
                sourceVehicleId, position, position, direction, direction, null, null, null,
                oldScore, newScore, newScore - oldScore, reason, checkpointId,
                null, null, null, null, null);
    }

    public static RoundEvent vehicleRespawned(VehicleState oldState, VehicleState newState) {
        return new RoundEvent(0, RoundEventType.VEHICLE_RESPAWNED, newState.vehicle().playerId(),
                newState.vehicle().id(), newState.vehicle().playerId(), newState.vehicle().id(),
                oldState.position(), newState.position(), oldState.orientation(), newState.orientation(),
                oldState.damage(), newState.damage(), newState.damage() - oldState.damage(),
                null, null, null, null, null, null, null, null, null, null);
    }
}
