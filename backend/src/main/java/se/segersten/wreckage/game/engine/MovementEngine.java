package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.ActionType;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.MovementResult;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.RoundEvent;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleStatus;
import se.segersten.wreckage.game.domain.VehicleTurn;

public class MovementEngine {

    public GameState resolveTurn(Turn turn, GameState gameState) {
        return resolveTurnWithEvents(turn, gameState).state();
    }

    public MovementResult resolveTurnWithEvents(Turn turn, GameState gameState) {
        return resolveTurnWithEvents(turn, gameState, new RegisterEffects());
    }

    public MovementResult resolveTurnWithEvents(Turn turn, GameState gameState, RegisterEffects effects) {
        Objects.requireNonNull(turn);
        Objects.requireNonNull(gameState);

        GameState result = gameState;
        List<RoundEvent> events = new ArrayList<>();
        for (VehicleTurn vehicleTurn : turn.vehicleTurns()) {
            MovementResult movement = applyOrder(result, vehicleTurn, effects);
            result = movement.state();
            events.addAll(movement.events());
        }
        return new MovementResult(result, events);
    }

    private MovementResult applyOrder(GameState gameState, VehicleTurn vehicleTurn, RegisterEffects effects) {
        List<VehicleState> currentStates = gameState.vehicleStates();
        Vehicle vehicle = vehicleTurn.vehicleState().vehicle();
        int vehicleIndex = indexOf(currentStates, vehicle);
        if (vehicleIndex < 0) {
            return new MovementResult(gameState, List.of());
        }

        VehicleState state = currentStates.get(vehicleIndex);
        if (!state.isActive()) {
            return new MovementResult(gameState, List.of());
        }
        return switch (vehicleTurn.movementOrder()) {
            case FORWARD_1 -> applyTranslation(gameState,vehicleIndex,state.orientation(),false,effects);
            case WAIT -> new MovementResult(gameState, List.of());
            case REVERSE_1 ->
                    applyTranslation(gameState,vehicleIndex,state.orientation().reverse(),false,effects);
            case TURN_LEFT -> applyTurn(gameState, vehicleIndex, state.orientation().turnLeft());
            case TURN_RIGHT -> applyTurn(gameState, vehicleIndex, state.orientation().turnRight());
            case FORWARD_2 -> applyForward(gameState, vehicleIndex, state, effects, 2);
            case FORWARD_3 -> applyForward(gameState, vehicleIndex, state, effects, 3);
            case U_TURN -> applyTurn(gameState, vehicleIndex, state.orientation().reverse());
            case LASER -> applyLaser(gameState, state, effects);
        };
    }

    private MovementResult applyLaser(GameState gameState, VehicleState shooter, RegisterEffects effects) {
        Position cursor = shooter.position();
        VehicleState target = null;
        while (!gameState.board().hasWall(cursor, shooter.orientation())) {
            Position next = cursor.move(shooter.orientation());
            if (!gameState.board().isValidPosition(next) || gameState.board().isObstacle(next)) break;
            cursor = next;
            int occupiedIndex = indexAt(gameState.vehicleStates(), cursor);
            if (occupiedIndex >= 0) {
                target = gameState.vehicleStates().get(occupiedIndex);
                break;
            }
        }

        List<RoundEvent> events = new ArrayList<>();
        events.add(withAction(event(RoundEventType.WEAPON_FIRED, shooter, shooter,
                at(shooter, cursor)), ActionType.LASER));
        if (target == null) return new MovementResult(gameState, List.copyOf(events));

        events.add(withAction(event(RoundEventType.WEAPON_HIT, shooter, target, target), ActionType.LASER));
        GameState current = gameState;
        for (int distance = 0; distance < 2; distance++) {
            MovementResult pushed = applyWeaponPush(current, target.vehicle().id(), shooter.orientation(),
                    shooter, effects);
            current = pushed.state();
            events.addAll(pushed.events().stream()
                    .map(event -> event.actionType() == null ? withAction(event, ActionType.LASER) : event)
                    .toList());
            if (pushed.events().stream().anyMatch(event -> event.type() == RoundEventType.PUSH_BLOCKED)) break;
        }
        return new MovementResult(current, List.copyOf(events));
    }

    private MovementResult applyForward(GameState gameState, int vehicleIndex, VehicleState state,
                                        RegisterEffects effects, int distance) {
        GameState current = gameState;
        int currentIndex = vehicleIndex;
        List<RoundEvent> events = new ArrayList<>();
        for (int step = 0; step < distance; step++) {
            MovementResult movement = applyTranslation(current, currentIndex, state.orientation(), false, effects);
            current = movement.state();
            events.addAll(movement.events());
            currentIndex = indexOf(current.vehicleStates(), state.vehicle());
            if (currentIndex < 0 || !current.vehicleStates().get(currentIndex).isActive()) break;
        }
        return new MovementResult(current, List.copyOf(events));
    }

    private MovementResult applyTurn(GameState gameState, int vehicleIndex, Direction orientation) {
        List<VehicleState> result = new ArrayList<>(gameState.vehicleStates());
        VehicleState state = result.get(vehicleIndex);
        VehicleState updated = new VehicleState(state.vehicle(), state.position(), orientation, state.status());
        result.set(vehicleIndex, updated);
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)),
                List.of(event(RoundEventType.TURN, state, updated)));
    }

    MovementResult applyConveyor(GameState state,UUID id,Direction direction, RegisterEffects effects){for(int i=0;i<state.vehicleStates().size();i++)if(state.vehicleStates().get(i).vehicle().id().equals(id)&&state.vehicleStates().get(i).isActive())return applyTranslation(state,i,direction,true,effects);return new MovementResult(state,List.of());}

    public MovementResult applyWeaponPush(GameState state, UUID targetVehicleId, Direction direction,
                                          VehicleState source) {
        return applyWeaponPush(state, targetVehicleId, direction, source, new RegisterEffects());
    }

    public MovementResult applyWeaponPush(GameState state, UUID targetVehicleId, Direction direction,
                                          VehicleState source, RegisterEffects effects) {
        for (int i = 0; i < state.vehicleStates().size(); i++) {
            VehicleState target = state.vehicleStates().get(i);
            if (target.vehicle().id().equals(targetVehicleId) && target.isActive()) {
                return applyDisplacement(state, i, direction, false, false, source, effects);
            }
        }
        return new MovementResult(state, List.of());
    }

    private MovementResult applyTranslation(GameState gameState,int vehicleIndex,Direction movementDirection,boolean conveyor, RegisterEffects effects) {
        return applyDisplacement(gameState, vehicleIndex, movementDirection, conveyor, true,
                gameState.vehicleStates().get(vehicleIndex), effects);
    }

    private MovementResult applyDisplacement(GameState gameState,int vehicleIndex,Direction movementDirection,
                                             boolean conveyor, boolean moveInitiator, VehicleState source,
                                             RegisterEffects effects) {
        List<VehicleState> currentStates = gameState.vehicleStates();
        VehicleState moving = currentStates.get(vehicleIndex);
        if (conveyor && effects.isShielded(moving.vehicle().id())) {
            return new MovementResult(gameState, List.of(event(RoundEventType.PUSH_BLOCKED, moving, moving)));
        }
        Position destination = moving.position().move(movementDirection);
        if (gameState.board().hasWall(moving.position(), movementDirection)) {
            return new MovementResult(gameState, moveInitiator ? List.of() : List.of(
                    event(RoundEventType.PUSH_BLOCKED, source, moving, moving)));
        }
        if (gameState.board().isObstacle(destination)) {
            return new MovementResult(gameState, moveInitiator ? List.of(event(RoundEventType.MOVE_BLOCKED, moving, moving))
                    : List.of(event(RoundEventType.PUSH_BLOCKED, source, moving, moving)));
        }
        if (!moveInitiator && !gameState.board().isValidPosition(destination)) {
            return new MovementResult(gameState, List.of(
                    event(RoundEventType.PUSH_BLOCKED, source, moving, moving)));
        }

        List<Integer> pushedIndexes = new ArrayList<>();
        if (!moveInitiator) pushedIndexes.add(vehicleIndex);
        boolean lethalDestination = isLethal(gameState, destination);
        int occupiedIndex = lethalDestination ? -1 : indexAt(currentStates, destination);
        while (!lethalDestination && occupiedIndex >= 0) {
            pushedIndexes.add(occupiedIndex);
            Position origin = destination;
            destination = destination.move(movementDirection);
            if (gameState.board().hasWall(origin, movementDirection)) {
                return new MovementResult(gameState, moveInitiator ? List.of() : List.of(
                        event(RoundEventType.PUSH_BLOCKED, source, moving, moving)));
            }
            if (gameState.board().isObstacle(destination)) {
                return new MovementResult(gameState, moveInitiator ? List.of(event(RoundEventType.MOVE_BLOCKED, moving, moving))
                        : List.of(event(RoundEventType.PUSH_BLOCKED, source, moving, moving)));
            }
            if (!gameState.board().isValidPosition(destination)) {
                return new MovementResult(gameState, moveInitiator ? List.of() : List.of(
                        event(RoundEventType.PUSH_BLOCKED, source, moving, moving)));
            }
            lethalDestination = isLethal(gameState, destination);
            occupiedIndex = lethalDestination ? -1 : indexAt(currentStates, destination);
        }

        for (int pushedIndex : pushedIndexes) {
            VehicleState protectedVehicle = currentStates.get(pushedIndex);
            if (effects.isShielded(protectedVehicle.vehicle().id())) {
                return new MovementResult(gameState, List.of(
                        event(RoundEventType.PUSH_BLOCKED, source, protectedVehicle, protectedVehicle)));
            }
        }

        List<VehicleState> result = new ArrayList<>(currentStates);
        List<RoundEvent> events = new ArrayList<>();
        int lastPushedIndex = pushedIndexes.size() - 1;
        if (lethalDestination) {
            VehicleState crashed = pushedIndexes.isEmpty()
                    ? moving
                    : currentStates.get(pushedIndexes.get(lastPushedIndex));
            VehicleState updated = new VehicleState(crashed.vehicle(), destination, crashed.orientation(),
                    VehicleStatus.CRASHED);
            result.set(pushedIndexes.isEmpty() ? vehicleIndex : pushedIndexes.get(lastPushedIndex), updated);
            events.add(event(conveyor?RoundEventType.CONVEYOR_CRASH:RoundEventType.CRASH,source,crashed,updated));
            if (pushedIndexes.isEmpty()) {
                return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
            }
            lastPushedIndex--;
        }

        for (int index = lastPushedIndex; index >= 0; index--) {
            int pushedIndex = pushedIndexes.get(index);
            VehicleState pushed = currentStates.get(pushedIndex);
            VehicleState updated = new VehicleState(pushed.vehicle(),
                    pushed.position().move(movementDirection), pushed.orientation(), pushed.status());
            result.set(pushedIndex, updated);
            events.add(event(conveyor?RoundEventType.CONVEYOR_PUSH:RoundEventType.PUSH,source,pushed,updated));
        }

        if (!moveInitiator) return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
        VehicleState updatedMoving = new VehicleState(moving.vehicle(),
                moving.position().move(movementDirection), moving.orientation(), moving.status());
        result.set(vehicleIndex, updatedMoving);
        RoundEventType type=pushedIndexes.isEmpty()?(conveyor?RoundEventType.CONVEYOR_MOVE:RoundEventType.MOVE):(conveyor?RoundEventType.CONVEYOR_RAM:RoundEventType.RAM);
        events.add(event(type, moving, updatedMoving));
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
    }

    private RoundEvent withAction(RoundEvent event, ActionType action) {
        return event.withAction(action);
    }

    private boolean isLethal(GameState gameState, Position destination) {
        return !gameState.board().isValidPosition(destination) || gameState.board().isPit(destination);
    }

    private RoundEvent event(RoundEventType type, VehicleState oldState, VehicleState newState) {
        Vehicle vehicle = oldState.vehicle();
        return new RoundEvent(0, type, vehicle.playerId(), vehicle.id(),
                vehicle.playerId(), vehicle.id(), oldState.position(), newState.position(),
                oldState.orientation(), newState.orientation());
    }

    private RoundEvent event(RoundEventType type, VehicleState source, VehicleState oldState,
                             VehicleState newState) {
        Vehicle subject = oldState.vehicle();
        Vehicle sourceVehicle = source.vehicle();
        return new RoundEvent(0, type, subject.playerId(), subject.id(), sourceVehicle.playerId(),
                sourceVehicle.id(), oldState.position(), newState.position(), oldState.orientation(),
                newState.orientation());
    }

    private VehicleState at(VehicleState state, Position position) {
        return new VehicleState(state.vehicle(), position, state.orientation(), state.status());
    }

    private int indexOf(List<VehicleState> states, Vehicle vehicle) {
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).vehicle() == vehicle) {
                return index;
            }
        }
        return -1;
    }

    private int indexAt(List<VehicleState> states, Position position) {
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).isActive() && states.get(index).position().equals(position)) {
                return index;
            }
        }
        return -1;
    }
}
