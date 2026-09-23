package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.VehicleState;

public final class CannonEngine {
    public CannonResult resolve(GameState gameState) {
        Objects.requireNonNull(gameState);
        GameState state = gameState;
        List<RoundEvent> events = new ArrayList<>();
        List<java.util.UUID> firingOrder = gameState.vehicleStates().stream()
                .map(vehicle -> vehicle.vehicle().id()).toList();
        for (java.util.UUID vehicleId : firingOrder) {
            VehicleState shooter = findById(state.vehicleStates(), vehicleId);
            ShotResult shot = fire(state, shooter);
            state = shot.state();
            events.addAll(shot.events());
        }
        return new CannonResult(state, events);
    }

    private ShotResult fire(GameState state, VehicleState shooter) {
        Position cursor = shooter.position();
        Position endpoint = shooter.position();
        VehicleState target = null;
        while (true) {
            cursor = cursor.move(shooter.orientation());
            if (!state.board().isValidPosition(cursor)) break;
            endpoint = cursor;
            if (state.board().blocksShot(cursor)) break;
            target = findAt(state.vehicleStates(), cursor);
            if (target != null) break;
        }

        List<RoundEvent> events = new ArrayList<>();
        events.add(event(RoundEventType.FIRE, shooter, shooter, shooter.position(), endpoint,
                shooter.damage(), shooter.damage()));
        if (target == null || state.board().blocksShot(endpoint)) return new ShotResult(state, events);

        events.add(event(RoundEventType.HIT, shooter, target, target.position(), target.position(),
                target.damage(), target.damage()));
        VehicleState damaged = new VehicleState(target.vehicle(), target.position(), target.orientation(),
                target.damage() + 1);
        events.add(event(RoundEventType.DAMAGE, shooter, damaged, damaged.position(), damaged.position(),
                target.damage(), damaged.damage()));
        List<VehicleState> updated = new ArrayList<>(state.vehicleStates());
        updated.set(updated.indexOf(target), damaged);
        return new ShotResult(new GameState(state.board(), updated), events);
    }

    private RoundEvent event(RoundEventType type, VehicleState source, VehicleState subject,
                             Position start, Position end, int oldDamage, int newDamage) {
        return new RoundEvent(0, type, subject.vehicle().playerId(), subject.vehicle().id(),
                source.vehicle().playerId(), source.vehicle().id(), start, end,
                subject.orientation(), subject.orientation(), oldDamage, newDamage);
    }

    private VehicleState findById(List<VehicleState> states, java.util.UUID id) {
        return states.stream().filter(state -> state.vehicle().id().equals(id)).findFirst().orElseThrow();
    }

    private VehicleState findAt(List<VehicleState> states, Position position) {
        return states.stream().filter(state -> state.position().equals(position)).findFirst().orElse(null);
    }

    private record ShotResult(GameState state, List<RoundEvent> events) {}
}
