package se.segersten.wreckage.game.domain;

public record VehicleState(Vehicle vehicle, Position position, Direction orientation, int damage) {
    public VehicleState(Vehicle vehicle, Position position, Direction orientation) {
        this(vehicle, position, orientation, 0);
    }

    public VehicleState {
        if (damage < 0) throw new IllegalArgumentException("damage must not be negative");
    }
}
