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
        return switch (vehicleTurn.movementOrder()) {
            case FORWARD -> applyTranslation(gameState, vehicleIndex, state.orientation());
            case REVERSE, MALFUNCTION_REVERSE ->
                    applyTranslation(gameState, vehicleIndex, state.orientation().reverse());
            case TURN_LEFT -> applyTurn(gameState, vehicleIndex, state.orientation().turnLeft());
            case TURN_RIGHT -> applyTurn(gameState, vehicleIndex, state.orientation().turnRight());
        };
    }

    private MovementResult applyTurn(GameState gameState, int vehicleIndex, Direction orientation) {
        List<VehicleState> result = new ArrayList<>(gameState.vehicleStates());
        VehicleState state = result.get(vehicleIndex);
        VehicleState updated = new VehicleState(state.vehicle(), state.position(), orientation, state.damage());
        result.set(vehicleIndex, updated);
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)),
                List.of(event(RoundEventType.TURN, state, updated)));
    }

    private MovementResult applyTranslation(
            GameState gameState, int vehicleIndex, Direction movementDirection) {
        List<VehicleState> currentStates = gameState.vehicleStates();
        VehicleState moving = currentStates.get(vehicleIndex);
        Position destination = moving.position().move(movementDirection);
        if (!gameState.board().isValidPosition(destination)) {
            return new MovementResult(gameState, List.of());
        }

        List<Integer> pushedIndexes = new ArrayList<>();
        int occupiedIndex = indexAt(currentStates, destination);
        while (occupiedIndex >= 0) {
            pushedIndexes.add(occupiedIndex);
            destination = destination.move(movementDirection);
            if (!gameState.board().isValidPosition(destination)) {
                return new MovementResult(gameState, List.of());
            }
            occupiedIndex = indexAt(currentStates, destination);
        }

        List<VehicleState> result = new ArrayList<>(currentStates);
        List<RoundEvent> events = new ArrayList<>();
        for (int index = pushedIndexes.size() - 1; index >= 0; index--) {
            int pushedIndex = pushedIndexes.get(index);
            VehicleState pushed = currentStates.get(pushedIndex);
            VehicleState updated = new VehicleState(pushed.vehicle(),
                    pushed.position().move(movementDirection), pushed.orientation(), pushed.damage());
            result.set(pushedIndex, updated);
            events.add(event(RoundEventType.PUSH, pushed, updated));
        }

        VehicleState updatedMoving = new VehicleState(moving.vehicle(),
                moving.position().move(movementDirection), moving.orientation(), moving.damage());
        result.set(vehicleIndex, updatedMoving);
        RoundEventType type = pushedIndexes.isEmpty() ? RoundEventType.MOVE : RoundEventType.RAM;
        events.add(event(type, moving, updatedMoving));
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), events);
    }

    private RoundEvent event(RoundEventType type, VehicleState oldState, VehicleState newState) {
        Vehicle vehicle = oldState.vehicle();
        return new RoundEvent(0, type, vehicle.playerId(), vehicle.id(),
                vehicle.playerId(), vehicle.id(), oldState.position(), newState.position(),
                oldState.orientation(), newState.orientation(), oldState.damage(), newState.damage());
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
            if (states.get(index).position().equals(position)) {
                return index;
            }
        }
        return -1;
    }
}
