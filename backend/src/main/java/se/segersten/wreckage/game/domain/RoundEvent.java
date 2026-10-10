package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public record RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                         UUID sourcePlayerId, UUID sourceVehicleId,
                         Position oldPosition, Position newPosition,
                         Direction oldDirection, Direction newDirection,
                         String checkpointId, ActionType actionType, Integer registerIndex) {
    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      Position oldPosition, Position newPosition, Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, playerId, vehicleId, oldPosition, newPosition,
                oldDirection, newDirection, null, null, null);
    }

    public RoundEvent(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                      UUID sourcePlayerId, UUID sourceVehicleId, Position oldPosition, Position newPosition,
                      Direction oldDirection, Direction newDirection) {
        this(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId, oldPosition, newPosition,
                oldDirection, newDirection, null, null, null);
    }

    public RoundEvent {
        Objects.requireNonNull(type); Objects.requireNonNull(playerId); Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(sourcePlayerId); Objects.requireNonNull(sourceVehicleId);
        Objects.requireNonNull(oldPosition); Objects.requireNonNull(newPosition);
        Objects.requireNonNull(oldDirection); Objects.requireNonNull(newDirection);
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        if (registerIndex != null && registerIndex < 1) throw new IllegalArgumentException("registerIndex must be positive");
    }

    public RoundEvent withSequence(int value) {
        return new RoundEvent(value, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, checkpointId, actionType, registerIndex);
    }

    public RoundEvent withRegister(int value) {
        return new RoundEvent(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, checkpointId, actionType, value);
    }

    public RoundEvent withAction(ActionType value) {
        return new RoundEvent(sequence, type, playerId, vehicleId, sourcePlayerId, sourceVehicleId,
                oldPosition, newPosition, oldDirection, newDirection, checkpointId, value, registerIndex);
    }

    public static RoundEvent checkpointCaptured(RoundEvent cause, String checkpointId) {
        return new RoundEvent(0, RoundEventType.CHECKPOINT_CAPTURED, cause.playerId(), cause.vehicleId(),
                cause.sourcePlayerId(), cause.sourceVehicleId(), cause.newPosition(), cause.newPosition(),
                cause.newDirection(), cause.newDirection(), checkpointId, cause.actionType(), cause.registerIndex());
    }

    public static RoundEvent vehicleRespawned(VehicleState oldState, VehicleState newState) {
        return new RoundEvent(0, RoundEventType.VEHICLE_RESPAWNED, newState.vehicle().playerId(),
                newState.vehicle().id(), oldState.position(), newState.position(),
                oldState.orientation(), newState.orientation());
    }
}
