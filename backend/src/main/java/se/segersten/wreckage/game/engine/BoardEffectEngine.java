package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleStatus;

public final class BoardEffectEngine {
    private final MovementEngine movement;
    public BoardEffectEngine(){this(new MovementEngine());}public BoardEffectEngine(MovementEngine movement){this.movement=java.util.Objects.requireNonNull(movement);}

    public BoardEffectResult resolve(GameState state) {
        List<RoundEvent> events = new ArrayList<>();
        GameState current=state;
        for(var c:state.board().conveyors()){var v=current.vehicleStates().stream().filter(VehicleState::isActive).filter(x->x.position().equals(c.position())).findFirst().orElse(null);if(v!=null){var m=movement.applyConveyor(current,v.vehicle().id(),c.direction());current=m.state();events.addAll(m.events());}}
        List<VehicleState> vehicles=new ArrayList<>(current.vehicleStates());
        for(int i=0;i<vehicles.size();i++){var v=vehicles.get(i);if(!v.isActive())continue;var r=state.board().rotatorAt(v.position());if(r==null)continue;var d=r.rotation()==se.segersten.wreckage.game.domain.Rotation.CLOCKWISE?v.orientation().turnRight():v.orientation().turnLeft();vehicles.set(i,new VehicleState(v.vehicle(),v.position(),d,v.status(),v.damage(),v.rocketAmmo()));events.add(new RoundEvent(0,RoundEventType.ROTATOR_TURN,v.vehicle().playerId(),v.vehicle().id(),v.position(),v.position(),v.orientation(),d));}
        for (int index = 0; index < vehicles.size(); index++) {
            VehicleState vehicle = vehicles.get(index);
            if (vehicle.isActive() && state.board().isPit(vehicle.position())) {
                VehicleState crashed = new VehicleState(vehicle.vehicle(), vehicle.position(),
                        vehicle.orientation(), VehicleStatus.CRASHED, vehicle.damage(), vehicle.rocketAmmo());
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
