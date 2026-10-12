package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record VehicleState(Vehicle vehicle, Position position, Direction orientation, VehicleStatus status,
                           boolean shieldAvailable) {
    public VehicleState(Vehicle vehicle, Position position, Direction orientation) {
        this(vehicle, position, orientation, VehicleStatus.ACTIVE, true);
    }

    public VehicleState(Vehicle vehicle, Position position, Direction orientation, VehicleStatus status) {
        this(vehicle, position, orientation, status, true);
    }

    public VehicleState {
        Objects.requireNonNull(vehicle);
        Objects.requireNonNull(position);
        Objects.requireNonNull(orientation);
        Objects.requireNonNull(status);
    }

    public boolean isActive() {
        return status == VehicleStatus.ACTIVE;
    }

    public VehicleState consumeShield() {
        if (!shieldAvailable) throw new IllegalStateException("Shield has already been consumed");
        return new VehicleState(vehicle, position, orientation, status, false);
    }
}
