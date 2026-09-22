package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleTurn;

public class MovementEngine {

    public GameState resolveTurn(Turn turn, GameState gameState) {
        Objects.requireNonNull(turn);
        Objects.requireNonNull(gameState);

        GameState result = gameState;
        for (VehicleTurn vehicleTurn : turn.vehicleTurns()) {
            result = applyOrder(result, vehicleTurn);
        }
        return result;
    }

    private GameState applyOrder(GameState gameState, VehicleTurn vehicleTurn) {
        List<VehicleState> currentStates = gameState.vehicleStates();
        Vehicle vehicle = vehicleTurn.vehicleState().vehicle();
        int vehicleIndex = indexOf(currentStates, vehicle);
        if (vehicleIndex < 0) {
            return gameState;
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
            return gameState;
        }

        List<VehicleState> result = new ArrayList<>(currentStates);
        result.set(vehicleIndex, new VehicleState(vehicle, position, orientation));
        return new GameState(gameState.board(), List.copyOf(result));
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
