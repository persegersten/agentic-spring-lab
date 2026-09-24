package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.VehicleState;

public final class BoardEffectEngine {

    public BoardEffectResult resolve(GameState state) {
        List<RoundEvent> events = new ArrayList<>();
        for (VehicleState vehicle : state.vehicleStates()) {
            if (state.board().isPit(vehicle.position())) {
                events.add(new RoundEvent(0, RoundEventType.PIT, vehicle.vehicle().playerId(),
                        vehicle.vehicle().id(), vehicle.position(), vehicle.position(),
                        vehicle.orientation(), vehicle.orientation()));
            }
        }
        return new BoardEffectResult(state, events);
    }
}
