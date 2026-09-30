package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import se.segersten.wreckage.game.domain.Direction;
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
        Objects.requireNonNull(turn);
        Objects.requireNonNull(gameState);

        GameState result = gameState;
        List<RoundEvent> events = new ArrayList<>();
        for (VehicleTurn vehicleTurn : turn.vehicleTurns()) {
            MovementResult movement = applyOrder(result, vehicleTurn);
            result = movement.state();
            events.addAll(movement.events());
        }
        return new MovementResult(result, events);
    }

    private MovementResult applyOrder(GameState gameState, VehicleTurn vehicleTurn) {
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
            case FORWARD_1 -> applyTranslation(gameState, vehicleIndex, state.orientation());
            case WAIT -> new MovementResult(gameState, List.of());
            case REVERSE_1 ->
                    applyTranslation(gameState, vehicleIndex, state.orientation().reverse());
            case TURN_LEFT -> applyTurn(gameState, vehicleIndex, state.orientation().turnLeft());
            case TURN_RIGHT -> applyTurn(gameState, vehicleIndex, state.orientation().turnRight());
            case FORWARD_2 -> applyForwardTwo(gameState, vehicleIndex, state);
            case U_TURN -> applyTurn(gameState, vehicleIndex, state.orientation().reverse());
        };
    }

    private MovementResult applyForwardTwo(GameState gameState, int vehicleIndex, VehicleState state) {
        MovementResult first = applyTranslation(gameState, vehicleIndex, state.orientation());
        int currentIndex = indexOf(first.state().vehicleStates(), state.vehicle());
        if (currentIndex < 0 || !first.state().vehicleStates().get(currentIndex).isActive()) {
            return first;
        }
        MovementResult second = applyTranslation(first.state(), currentIndex, state.orientation());
        List<RoundEvent> events = new ArrayList<>(first.events());
        events.addAll(second.events());
        return new MovementResult(second.state(), List.copyOf(events));
    }

    private MovementResult applyTurn(GameState gameState, int vehicleIndex, Direction orientation) {
        List<VehicleState> result = new ArrayList<>(gameState.vehicleStates());
        VehicleState state = result.get(vehicleIndex);
        VehicleState updated = new VehicleState(state.vehicle(), state.position(), orientation, state.damage(),
                state.status());
        result.set(vehicleIndex, updated);
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)),
                List.of(event(RoundEventType.TURN, state, updated)));
    }

    private MovementResult applyTranslation(
            GameState gameState, int vehicleIndex, Direction movementDirection) {
        List<VehicleState> currentStates = gameState.vehicleStates();
        VehicleState moving = currentStates.get(vehicleIndex);
        Position destination = moving.position().move(movementDirection);
        if (gameState.board().hasWall(moving.position(), movementDirection)) {
            return new MovementResult(gameState, List.of());
        }

        List<Integer> pushedIndexes = new ArrayList<>();
        boolean lethalDestination = isLethal(gameState, destination);
        int occupiedIndex = lethalDestination ? -1 : indexAt(currentStates, destination);
        while (!lethalDestination && occupiedIndex >= 0) {
            pushedIndexes.add(occupiedIndex);
            Position origin = destination;
            destination = destination.move(movementDirection);
            if (gameState.board().hasWall(origin, movementDirection)) {
                return new MovementResult(gameState, List.of());
            }
            lethalDestination = isLethal(gameState, destination);
            occupiedIndex = lethalDestination ? -1 : indexAt(currentStates, destination);
        }

        List<VehicleState> result = new ArrayList<>(currentStates);
        List<RoundEvent> events = new ArrayList<>();
        int lastPushedIndex = pushedIndexes.size() - 1;
        if (lethalDestination) {
            VehicleState crashed = pushedIndexes.isEmpty()
                    ? moving
                    : currentStates.get(pushedIndexes.get(lastPushedIndex));
            VehicleState updated = new VehicleState(crashed.vehicle(), destination, crashed.orientation(),
                    crashed.damage(), VehicleStatus.CRASHED);
            result.set(pushedIndexes.isEmpty() ? vehicleIndex : pushedIndexes.get(lastPushedIndex), updated);
            events.add(event(RoundEventType.CRASH, moving, crashed, updated));
            if (pushedIndexes.isEmpty()) {
                return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
            }
            lastPushedIndex--;
        }

        for (int index = lastPushedIndex; index >= 0; index--) {
            int pushedIndex = pushedIndexes.get(index);
            VehicleState pushed = currentStates.get(pushedIndex);
            VehicleState updated = new VehicleState(pushed.vehicle(),
                    pushed.position().move(movementDirection), pushed.orientation(), pushed.damage(),
                    pushed.status());
            result.set(pushedIndex, updated);
            events.add(event(RoundEventType.PUSH, pushed, updated));
        }

        VehicleState updatedMoving = new VehicleState(moving.vehicle(),
                moving.position().move(movementDirection), moving.orientation(), moving.damage(), moving.status());
        result.set(vehicleIndex, updatedMoving);
        RoundEventType type = pushedIndexes.isEmpty() ? RoundEventType.MOVE : RoundEventType.RAM;
        events.add(event(type, moving, updatedMoving));
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
    }

    private boolean isLethal(GameState gameState, Position destination) {
        return !gameState.board().isValidPosition(destination) || gameState.board().isPit(destination);
    }

    private RoundEvent event(RoundEventType type, VehicleState oldState, VehicleState newState) {
        Vehicle vehicle = oldState.vehicle();
        return new RoundEvent(0, type, vehicle.playerId(), vehicle.id(),
                vehicle.playerId(), vehicle.id(), oldState.position(), newState.position(),
                oldState.orientation(), newState.orientation(), oldState.damage(), newState.damage());
    }

    private RoundEvent event(RoundEventType type, VehicleState source, VehicleState oldState,
                             VehicleState newState) {
        Vehicle subject = oldState.vehicle();
        Vehicle sourceVehicle = source.vehicle();
        return new RoundEvent(0, type, subject.playerId(), subject.id(), sourceVehicle.playerId(),
                sourceVehicle.id(), oldState.position(), newState.position(), oldState.orientation(),
                newState.orientation(), oldState.damage(), newState.damage());
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
