package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import se.segersten.wreckage.game.domain.*;

public class ActionEngine {
    private final MovementEngine movementEngine;

    public ActionEngine() { this(new MovementEngine()); }
    public ActionEngine(MovementEngine movementEngine) { this.movementEngine = movementEngine; }

    public ActionResult resolve(ActionTiming timing, int registerIndex, GameState state,
                                Map<UUID, PlayerProgram> programs, List<UUID> initiative) {
        GameState current = state;
        List<RoundEvent> events = new ArrayList<>();
        for (UUID playerId : initiative) {
            PlayerProgram program = programs.get(playerId);
            ScheduledAction action = program == null ? null : program.scheduledAction();
            if (action != null && action.registerIndex() == registerIndex
                    && action.actionType().timing() == timing && activeVehicle(current, playerId) != null) {
                ActionResult result = encounter(playerId, action, timing, current);
                current = result.state();
                events.addAll(result.events());
            }
        }
        return new ActionResult(current, events);
    }

    protected ActionResult encounter(UUID playerId, ScheduledAction action, ActionTiming timing, GameState state) {
        if (!action.actionType().isWeapon()) return new ActionResult(state, List.of());
        VehicleState shooter = activeVehicle(state, playerId);
        if (shooter == null || action.actionType() == ActionType.ROCKET && shooter.rocketAmmo() == 0)
            return new ActionResult(state, List.of());

        ActionType weapon = action.actionType();
        GameState current = state;
        List<RoundEvent> events = new ArrayList<>();
        Shot shot = trace(current, shooter, weapon.weaponRange());
        events.add(event(RoundEventType.WEAPON_FIRED, weapon, shooter, shooter, shooter.position(),
                shot.endpoint(), null, null, null, null));
        if (weapon == ActionType.ROCKET) {
            VehicleState consumed = new VehicleState(shooter.vehicle(), shooter.position(), shooter.orientation(),
                    shooter.status(), shooter.damage(), 0);
            current = replace(current, shooter, consumed);
            events.add(event(RoundEventType.AMMO_CHANGED, weapon, consumed, consumed, consumed.position(),
                    consumed.position(), null, null, 1, 0));
            shooter = consumed;
        }
        if (shot.target() == null) return new ActionResult(current, events);
        VehicleState target = shot.target();
        events.add(event(RoundEventType.WEAPON_HIT, weapon, shooter, target, target.position(),
                target.position(), null, null, null, null));

        if (weapon == ActionType.REPULSOR) {
            MovementResult pushed = movementEngine.applyWeaponPush(current, target.vehicle().id(),
                    shooter.orientation(), shooter);
            current = pushed.state();
            for (RoundEvent pushEvent : pushed.events()) events.add(withWeapon(pushEvent, weapon));
            return new ActionResult(current, events);
        }

        int newDamage = target.damage() + weapon.weaponDamage();
        VehicleStatus status = newDamage >= 3 ? VehicleStatus.CRASHED : target.status();
        VehicleState damaged = new VehicleState(target.vehicle(), target.position(), target.orientation(), status,
                newDamage, target.rocketAmmo());
        current = replace(current, target, damaged);
        events.add(event(RoundEventType.DAMAGE_APPLIED, weapon, shooter, target, target.position(), target.position(),
                target.damage(), newDamage, null, null));
        if (status == VehicleStatus.CRASHED)
            events.add(event(RoundEventType.VEHICLE_CRASHED, weapon, shooter, damaged, damaged.position(),
                    damaged.position(), newDamage, newDamage, null, null));
        return new ActionResult(current, events);
    }

    private Shot trace(GameState state, VehicleState shooter, int range) {
        Position cursor = shooter.position();
        for (int distance = 0; distance < range; distance++) {
            if (state.board().hasWall(cursor, shooter.orientation())) break;
            Position next = cursor.move(shooter.orientation());
            if (!state.board().isValidPosition(next)) break;
            cursor = next;
            VehicleState target = state.vehicleStates().stream().filter(VehicleState::isActive)
                    .filter(vehicle -> vehicle.position().equals(next)).findFirst().orElse(null);
            if (target != null) return new Shot(cursor, target);
        }
        return new Shot(cursor, null);
    }

    private VehicleState activeVehicle(GameState state, UUID playerId) {
        return state.vehicleStates().stream().filter(VehicleState::isActive)
                .filter(v -> v.vehicle().playerId().equals(playerId)).findFirst().orElse(null);
    }

    private GameState replace(GameState state, VehicleState oldState, VehicleState newState) {
        List<VehicleState> vehicles = new ArrayList<>(state.vehicleStates());
        vehicles.set(vehicles.indexOf(oldState), newState);
        return new GameState(state.board(), List.copyOf(vehicles));
    }

    private RoundEvent withWeapon(RoundEvent e, ActionType weapon) {
        return new RoundEvent(0, e.type(), e.playerId(), e.vehicleId(), e.sourcePlayerId(), e.sourceVehicleId(),
                e.oldPosition(), e.newPosition(), e.oldDirection(), e.newDirection(), e.oldDamage(), e.newDamage(),
                e.damageDelta(), e.oldScore(), e.newScore(), e.scoreDelta(), e.scoreReason(), e.checkpointId(),
                weapon, e.oldAmmo(), e.newAmmo(), e.ammoDelta());
    }

    private RoundEvent event(RoundEventType type, ActionType weapon, VehicleState source, VehicleState subject,
                             Position oldPosition, Position newPosition, Integer oldDamage, Integer newDamage,
                             Integer oldAmmo, Integer newAmmo) {
        return new RoundEvent(0, type, subject.vehicle().playerId(), subject.vehicle().id(),
                source.vehicle().playerId(), source.vehicle().id(), oldPosition, newPosition,
                subject.orientation(), subject.orientation(), oldDamage, newDamage,
                oldDamage == null ? null : newDamage - oldDamage, null, null, null, null, null,
                weapon, oldAmmo, newAmmo, oldAmmo == null ? null : newAmmo - oldAmmo);
    }

    private record Shot(Position endpoint, VehicleState target) {}
}
