package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record VehicleState(Vehicle vehicle, Position position, Direction orientation, int damage,
                           VehicleStatus status) {
    public VehicleState(Vehicle vehicle, Position position, Direction orientation) {
        this(vehicle, position, orientation, 0, VehicleStatus.ACTIVE);
    }

    public VehicleState(Vehicle vehicle, Position position, Direction orientation, int damage) {
        this(vehicle, position, orientation, damage, VehicleStatus.ACTIVE);
    }

    public VehicleState {
        Objects.requireNonNull(vehicle);
        Objects.requireNonNull(position);
        Objects.requireNonNull(orientation);
        Objects.requireNonNull(status);
        if (damage < 0) throw new IllegalArgumentException("damage must not be negative");
    }

    public boolean isActive() {
        return status == VehicleStatus.ACTIVE;
    }
}
