package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleStatus;

public final class BoardEffectEngine {

    public BoardEffectResult resolve(GameState state) {
        List<RoundEvent> events = new ArrayList<>();
        List<VehicleState> vehicles = new ArrayList<>(state.vehicleStates());
        for (int index = 0; index < vehicles.size(); index++) {
            VehicleState vehicle = vehicles.get(index);
            if (vehicle.isActive() && state.board().isPit(vehicle.position())) {
                VehicleState crashed = new VehicleState(vehicle.vehicle(), vehicle.position(),
                        vehicle.orientation(), vehicle.damage(), VehicleStatus.CRASHED);
                vehicles.set(index, crashed);
                events.add(new RoundEvent(0, RoundEventType.CRASH, vehicle.vehicle().playerId(),
                        vehicle.vehicle().id(), vehicle.position(), vehicle.position(),
                        vehicle.orientation(), vehicle.orientation()));
            }
        }
        return events.isEmpty() ? new BoardEffectResult(state, events)
                : new BoardEffectResult(new GameState(state.board(), List.copyOf(vehicles)), events);
    }
}
