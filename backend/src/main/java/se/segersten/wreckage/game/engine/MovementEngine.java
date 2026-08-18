package se.segersten.wreckage.game.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleTurn;

public class MovementEngine {

    public GameState resolveTurn(Turn turn, GameState gameState) {
        Map<Vehicle, MovementOrder> orders = ordersByVehicle(turn);
        List<VehicleState> currentStates = gameState.vehicleStates();
        List<VehicleState> intentions = new ArrayList<>(currentStates.size());

        for (VehicleState state : currentStates) {
            MovementOrder order = orders.get(state.vehicle());
            intentions.add(order == null ? state : applyOrder(state, order, gameState.board()));
        }

        Set<Vehicle> blocked = blockedMovements(currentStates, intentions);
        List<VehicleState> result = new ArrayList<>(currentStates.size());
        for (int i = 0; i < currentStates.size(); i++) {
            VehicleState current = currentStates.get(i);
            VehicleState intended = intentions.get(i);
            result.add(blocked.contains(current.vehicle()) ? current : intended);
        }

        return new GameState(gameState.board(), List.copyOf(result));
    }

    private Map<Vehicle, MovementOrder> ordersByVehicle(Turn turn) {
        Map<Vehicle, MovementOrder> orders = new IdentityHashMap<>();
        for (VehicleTurn vehicleTurn : turn.vehicleTurns()) {
            orders.put(vehicleTurn.vehicleState().vehicle(), vehicleTurn.movementOrder());
        }
        return orders;
    }

    private VehicleState applyOrder(VehicleState state, MovementOrder order, Board board) {
        Position position = state.position();
        Direction orientation = state.orientation();

        switch (order) {
            case FORWARD -> position = position.move(orientation);
            case TURN_LEFT -> orientation = orientation.turnLeft();
            case TURN_RIGHT -> orientation = orientation.turnRight();
        }

        if (!board.isValidPosition(position)) {
            return state;
        }
        return new VehicleState(state.vehicle(), position, orientation);
    }

    private Set<Vehicle> blockedMovements(
            List<VehicleState> currentStates, List<VehicleState> intentions) {
        Map<Position, VehicleState> currentByPosition = new HashMap<>();
        Map<Position, List<VehicleState>> moversByTarget = new HashMap<>();
        Map<Vehicle, VehicleState> intentionByVehicle = new IdentityHashMap<>();
        Set<Vehicle> movers = identitySet();
        Set<Vehicle> blocked = identitySet();

        for (int i = 0; i < currentStates.size(); i++) {
            VehicleState current = currentStates.get(i);
            VehicleState intended = intentions.get(i);
            currentByPosition.put(current.position(), current);
            intentionByVehicle.put(current.vehicle(), intended);

            if (!current.position().equals(intended.position())) {
                movers.add(current.vehicle());
                moversByTarget.computeIfAbsent(intended.position(), ignored -> new ArrayList<>())
                        .add(current);
            }
        }

        // All vehicles aiming for the same cell are blocked.
        for (List<VehicleState> contenders : moversByTarget.values()) {
            if (contenders.size() > 1) {
                contenders.forEach(state -> blocked.add(state.vehicle()));
            }
        }

        // A two-vehicle position swap is explicitly forbidden.
        for (VehicleState current : currentStates) {
            if (!movers.contains(current.vehicle())) {
                continue;
            }
            VehicleState occupant = currentByPosition.get(
                    intentionByVehicle.get(current.vehicle()).position());
            if (occupant != null && movers.contains(occupant.vehicle())
                    && intentionByVehicle.get(occupant.vehicle()).position().equals(current.position())) {
                blocked.add(current.vehicle());
                blocked.add(occupant.vehicle());
            }
        }

        // Propagate blocking through chains such as A -> B -> stationary C.
        boolean changed;
        do {
            changed = false;
            for (VehicleState current : currentStates) {
                if (!movers.contains(current.vehicle()) || blocked.contains(current.vehicle())) {
                    continue;
                }
                VehicleState occupant = currentByPosition.get(
                        intentionByVehicle.get(current.vehicle()).position());
                if (occupant != null
                        && (!movers.contains(occupant.vehicle()) || blocked.contains(occupant.vehicle()))) {
                    changed |= blocked.add(current.vehicle());
                }
            }
        } while (changed);

        return blocked;
    }

    private Set<Vehicle> identitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
