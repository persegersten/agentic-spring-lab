package se.segersten.wreckage.game.domain;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public record Turn(List<VehicleTurn> vehicleTurns) {

    public Turn {
        Set<Vehicle> vehicles = Collections.newSetFromMap(new IdentityHashMap<>());
        for (VehicleTurn vehicleTurn : vehicleTurns) {
            if (!vehicles.add(vehicleTurn.vehicleState().vehicle())) {
                throw new IllegalArgumentException("A vehicle can only have one order per turn");
            }
        }
    }
}
