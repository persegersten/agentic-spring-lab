package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record VehicleState(Vehicle vehicle, Position position, Direction orientation, VehicleStatus status) {
    public VehicleState(Vehicle vehicle, Position position, Direction orientation) {
        this(vehicle, position, orientation, VehicleStatus.ACTIVE);
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
}
