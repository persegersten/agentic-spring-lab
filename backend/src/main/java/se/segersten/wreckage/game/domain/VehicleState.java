package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record VehicleState(Vehicle vehicle, Position position, Direction orientation, VehicleStatus status, int damage,
                           int rocketAmmo) {
    public VehicleState(Vehicle vehicle, Position position, Direction orientation) {
        this(vehicle, position, orientation, VehicleStatus.ACTIVE, 0, 1);
    }

    public VehicleState(Vehicle vehicle, Position position, Direction orientation, VehicleStatus status) {
        this(vehicle, position, orientation, status, 0, 1);
    }

    public VehicleState(Vehicle vehicle, Position position, Direction orientation, VehicleStatus status, int damage) {
        this(vehicle, position, orientation, status, damage, 1);
    }

    public VehicleState {
        Objects.requireNonNull(vehicle);
        Objects.requireNonNull(position);
        Objects.requireNonNull(orientation);
        Objects.requireNonNull(status);
        if (damage < 0) throw new IllegalArgumentException("damage must not be negative");
        if (rocketAmmo < 0 || rocketAmmo > 1) throw new IllegalArgumentException("rocketAmmo must be between 0 and 1");
    }

    public boolean isActive() {
        return status == VehicleStatus.ACTIVE;
    }
}
