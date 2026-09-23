package se.segersten.wreckage.game.api;

import java.util.UUID;
import se.segersten.wreckage.game.domain.*;

public record RoundEventResponse(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                                 UUID sourcePlayerId, UUID sourceVehicleId,
                                 Position oldPosition, Position newPosition,
                                 Direction oldDirection, Direction newDirection,
                                 int oldDamage, int newDamage) {
    static RoundEventResponse from(RoundEvent event) {
        return new RoundEventResponse(event.sequence(), event.type(), event.playerId(), event.vehicleId(),
                event.sourcePlayerId(), event.sourceVehicleId(), event.oldPosition(), event.newPosition(),
                event.oldDirection(), event.newDirection(), event.oldDamage(), event.newDamage());
    }
}
