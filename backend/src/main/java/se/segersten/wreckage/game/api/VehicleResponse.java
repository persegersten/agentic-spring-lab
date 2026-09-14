package se.segersten.wreckage.game.api;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record VehicleResponse(UUID id, UUID playerId, int x, int y, Direction direction) {
    static VehicleResponse from(VehicleState s) { return new VehicleResponse(s.vehicle().id(),s.vehicle().playerId(),s.position().x(),s.position().y(),s.orientation()); }
}
