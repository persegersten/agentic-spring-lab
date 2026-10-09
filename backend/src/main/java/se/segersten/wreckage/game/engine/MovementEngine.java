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
        };
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
        VehicleState updated = new VehicleState(state.vehicle(), state.position(), orientation, state.status(), state.damage(), state.rocketAmmo());
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
        if (conveyor && effects.isAnchored(moving.vehicle().id())) {
            return new MovementResult(gameState, List.of(withAction(
                    event(RoundEventType.PUSH_BLOCKED, moving, moving), ActionType.ANCHOR)));
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
            lethalDestination = isLethal(gameState, destination);
            occupiedIndex = lethalDestination ? -1 : indexAt(currentStates, destination);
        }

        for (int pushedIndex : pushedIndexes) {
            VehicleState anchored = currentStates.get(pushedIndex);
            if (effects.isAnchored(anchored.vehicle().id())) {
                return new MovementResult(gameState, List.of(withAction(
                        event(RoundEventType.PUSH_BLOCKED, source, anchored, anchored), ActionType.ANCHOR)));
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
                    VehicleStatus.CRASHED, crashed.damage(), crashed.rocketAmmo());
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
                    pushed.position().move(movementDirection), pushed.orientation(), pushed.status(), pushed.damage(), pushed.rocketAmmo());
            result.set(pushedIndex, updated);
            events.add(event(conveyor?RoundEventType.CONVEYOR_PUSH:RoundEventType.PUSH,source,pushed,updated));
        }

        if (!moveInitiator) return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
        VehicleState updatedMoving = new VehicleState(moving.vehicle(),
                moving.position().move(movementDirection), moving.orientation(), moving.status(), moving.damage(), moving.rocketAmmo());
        result.set(vehicleIndex, updatedMoving);
        RoundEventType type=pushedIndexes.isEmpty()?(conveyor?RoundEventType.CONVEYOR_MOVE:RoundEventType.MOVE):(conveyor?RoundEventType.CONVEYOR_RAM:RoundEventType.RAM);
        events.add(event(type, moving, updatedMoving));
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
    }

    public MovementResult applyTurbo(GameState state, UUID vehicleId, RegisterEffects effects) {
        for (int i = 0; i < state.vehicleStates().size(); i++) {
            VehicleState vehicle = state.vehicleStates().get(i);
            if (vehicle.vehicle().id().equals(vehicleId) && vehicle.isActive()) {
                MovementResult result = applyTranslation(state, i, vehicle.orientation(), false, effects);
                return tagged(result, ActionType.TURBO);
            }
        }
        return new MovementResult(state, List.of());
    }

    public MovementResult applySideStep(GameState state, UUID vehicleId, boolean left) {
        for (int i = 0; i < state.vehicleStates().size(); i++) {
            VehicleState vehicle = state.vehicleStates().get(i);
            if (!vehicle.vehicle().id().equals(vehicleId) || !vehicle.isActive()) continue;
            ActionType action = left ? ActionType.SIDE_STEP_LEFT : ActionType.SIDE_STEP_RIGHT;
            Direction direction = left ? vehicle.orientation().turnLeft() : vehicle.orientation().turnRight();
            Position destination = vehicle.position().move(direction);
            if (state.board().hasWall(vehicle.position(), direction)
                    || state.board().isObstacle(destination)
                    || state.board().isValidPosition(destination) && indexAt(state.vehicleStates(), destination) >= 0) {
                return new MovementResult(state, List.of(withAction(event(RoundEventType.MOVE_BLOCKED,
                        vehicle, vehicle), action)));
            }
            List<VehicleState> vehicles = new ArrayList<>(state.vehicleStates());
            VehicleStatus status = isLethal(state, destination) ? VehicleStatus.CRASHED : vehicle.status();
            VehicleState updated = new VehicleState(vehicle.vehicle(), destination, vehicle.orientation(), status,
                    vehicle.damage(), vehicle.rocketAmmo());
            vehicles.set(i, updated);
            RoundEventType type = status == VehicleStatus.CRASHED ? RoundEventType.CRASH : RoundEventType.SIDE_STEP;
            return new MovementResult(new GameState(state.board(), List.copyOf(vehicles)),
                    List.of(withAction(event(type, vehicle, updated), action)));
        }
        return new MovementResult(state, List.of());
    }

    private MovementResult tagged(MovementResult result, ActionType action) {
        return new MovementResult(result.state(), result.events().stream().map(event -> withAction(event, action)).toList());
    }

    private RoundEvent withAction(RoundEvent event, ActionType action) {
        return new RoundEvent(event.sequence(), event.type(), event.playerId(), event.vehicleId(),
                event.sourcePlayerId(), event.sourceVehicleId(), event.oldPosition(), event.newPosition(),
                event.oldDirection(), event.newDirection(), event.oldDamage(), event.newDamage(), event.damageDelta(),
                event.oldScore(), event.newScore(), event.scoreDelta(), event.scoreReason(), event.checkpointId(),
                action, event.oldAmmo(), event.newAmmo(), event.ammoDelta());
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
