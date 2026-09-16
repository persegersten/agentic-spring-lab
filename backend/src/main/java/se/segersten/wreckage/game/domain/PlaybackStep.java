package se.segersten.wreckage.game.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlaybackStep(int index, Map<UUID, MovementOrder> commands, List<VehicleState> vehicleStates) {
    public PlaybackStep {
        commands = Map.copyOf(commands);
        vehicleStates = List.copyOf(vehicleStates);
    }
}
