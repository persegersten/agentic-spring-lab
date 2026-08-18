package se.segersten.wreckage.game.domain;

import java.util.List;

public record GameState(Board board, List<VehicleState> vehicleStates) {

}
