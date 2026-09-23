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
        Position position = state.position();
        Direction orientation = state.orientation();

        switch (vehicleTurn.movementOrder()) {
            case FORWARD -> position = position.move(orientation);
            case REVERSE -> position = position.move(orientation.reverse());
            case TURN_LEFT -> orientation = orientation.turnLeft();
            case TURN_RIGHT -> orientation = orientation.turnRight();
        }

        if (!gameState.board().isValidPosition(position)
                || isOccupiedByAnotherVehicle(currentStates, vehicle, position)) {
            return new MovementResult(gameState, List.of());
        }

        List<VehicleState> result = new ArrayList<>(currentStates);
        VehicleState updated = new VehicleState(vehicle, position, orientation);
        result.set(vehicleIndex, updated);
        RoundEvent event = new RoundEvent(0,
                position.equals(state.position()) ? RoundEventType.TURN : RoundEventType.MOVE,
                vehicle.playerId(), vehicle.id(), state.position(), updated.position(),
                state.orientation(), updated.orientation());
        return new MovementResult(new GameState(gameState.board(), List.copyOf(result)), List.of(event));
    }

    private int indexOf(List<VehicleState> states, Vehicle vehicle) {
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).vehicle() == vehicle) {
                return index;
            }
        }
        return -1;
    }

    private boolean isOccupiedByAnotherVehicle(
            List<VehicleState> states, Vehicle vehicle, Position position) {
        return states.stream().anyMatch(state -> state.vehicle() != vehicle
                && state.position().equals(position));
    }
}
