package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import se.segersten.wreckage.game.domain.ActionTiming;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.ScheduledAction;
import se.segersten.wreckage.game.domain.ActionType;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleStatus;

public class ActionEngine {
    private static final int LASER_RANGE = 6;

    public ActionResult resolve(ActionTiming timing, int registerIndex, GameState state,
                             Map<UUID, PlayerProgram> programs, List<UUID> initiative) {
        GameState current = state;
        List<RoundEvent> events = new ArrayList<>();
        for (UUID playerId : initiative) {
            PlayerProgram program = programs.get(playerId);
            ScheduledAction action = program == null ? null : program.scheduledAction();
            if (action != null && action.registerIndex() == registerIndex
                    && action.actionType().timing() == timing
                    && current.vehicleStates().stream().anyMatch(vehicle -> vehicle.isActive()
                            && vehicle.vehicle().playerId().equals(playerId))) {
                ActionResult result = encounter(playerId, action, timing, current);
                current = result.state();
                events.addAll(result.events());
            }
        }
        return new ActionResult(current, events);
    }

    protected ActionResult encounter(UUID playerId, ScheduledAction action, ActionTiming timing, GameState state) {
        if (action.actionType() != ActionType.LASER) return new ActionResult(state, List.of());
        return fireLaser(playerId, state);
    }

    private ActionResult fireLaser(UUID playerId, GameState state) {
        VehicleState shooter = state.vehicleStates().stream()
                .filter(VehicleState::isActive)
                .filter(vehicle -> vehicle.vehicle().playerId().equals(playerId))
                .findFirst().orElse(null);
        if (shooter == null) return new ActionResult(state, List.of());

        Position cursor = shooter.position();
        VehicleState target = null;
        for (int distance = 0; distance < LASER_RANGE; distance++) {
            if (state.board().hasWall(cursor, shooter.orientation())) break;
            Position next = cursor.move(shooter.orientation());
            if (!state.board().isValidPosition(next)) break;
            cursor = next;
            target = state.vehicleStates().stream()
                    .filter(VehicleState::isActive)
                    .filter(vehicle -> vehicle.position().equals(next))
                    .findFirst().orElse(null);
            if (target != null) break;
        }

        List<RoundEvent> events = new ArrayList<>();
        events.add(event(RoundEventType.WEAPON_FIRED, shooter, shooter, shooter.position(), cursor,
                null, null));
        if (target == null) return new ActionResult(state, events);

        events.add(event(RoundEventType.WEAPON_HIT, shooter, target, target.position(), target.position(),
                null, null));
        int newDamage = target.damage() + 1;
        VehicleStatus status = newDamage >= 3 ? VehicleStatus.CRASHED : target.status();
        VehicleState damaged = new VehicleState(target.vehicle(), target.position(), target.orientation(), status,
                newDamage);
        List<VehicleState> vehicles = new ArrayList<>(state.vehicleStates());
        vehicles.set(vehicles.indexOf(target), damaged);
        events.add(event(RoundEventType.DAMAGE_APPLIED, shooter, target, target.position(), target.position(),
                target.damage(), newDamage));
        if (status == VehicleStatus.CRASHED) {
            events.add(event(RoundEventType.VEHICLE_CRASHED, shooter, target, target.position(), target.position(),
                    newDamage, newDamage));
        }
        return new ActionResult(new GameState(state.board(), List.copyOf(vehicles)), events);
    }

    private RoundEvent event(RoundEventType type, VehicleState source, VehicleState subject,
                             Position oldPosition, Position newPosition, Integer oldDamage, Integer newDamage) {
        return new RoundEvent(0, type, subject.vehicle().playerId(), subject.vehicle().id(),
                source.vehicle().playerId(), source.vehicle().id(), oldPosition, newPosition,
                subject.orientation(), subject.orientation(), oldDamage, newDamage,
                oldDamage == null ? null : newDamage - oldDamage, null, null, null, null, null);
    }
}
