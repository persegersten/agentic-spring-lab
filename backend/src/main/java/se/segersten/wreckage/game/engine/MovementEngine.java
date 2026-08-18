package se.segersten.wreckage.game.engine;

import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.VehicleTurn;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Direction;
import java.util.ArrayList;
import java.util.List;

public class MovementEngine {

    public GameState resolveTurn(Turn turn, GameState gameState) {
        // Create a new list to hold the updated vehicle states
        List<VehicleState> pendingVehicleStates = moveVehicles(turn.vehicleTurns(), gameState);

        List<VehicleTurn> acceptedVehicleTurns = applyRules(pendingVehicleStates, gameState);

        List<VehicleState> newVehicleStates = moveVehicles(acceptedVehicleTurns, gameState);


        return new GameState(gameState.board(), newVehicleStates);
    }


    private VehicleState moveVehicle(VehicleState vehicleState, MovementOrder movementOrder, Board board) {
        Position newPosition = vehicleState.position();
        Direction orientation = vehicleState.orientation();

        switch (movementOrder) {
            case FORWARD -> newPosition = newPosition.move(orientation);
            case TURN_LEFT -> orientation = orientation.turnLeft();
            case TURN_RIGHT -> orientation = orientation.turnRight();
        }

        if (board.isValidPosition(newPosition)) {
            return new VehicleState(vehicleState.vehicle(), newPosition, orientation);
        } else {
            // If the move is invalid, return the original state
            return vehicleState;
        }
    }

    // Accoring to the rules 10. Collision Rules and 11. Movement Order Rules
    private List<VehicleTurn> applyRules(List<VehicleState> vehicleStates, GameState gameState) {
        
    }
}

        }

        return newVehicleStates;
    }
}
