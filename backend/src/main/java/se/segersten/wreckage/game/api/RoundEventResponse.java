package se.segersten.wreckage.game.api;

import java.util.UUID;
import se.segersten.wreckage.game.domain.*;

public record RoundEventResponse(int sequence, RoundEventType type, UUID playerId, UUID vehicleId,
                                 Position oldPosition, Position newPosition,
                                 Direction oldDirection, Direction newDirection) {
    static RoundEventResponse from(RoundEvent event) {
        return new RoundEventResponse(event.sequence(), event.type(), event.playerId(), event.vehicleId(),
                event.oldPosition(), event.newPosition(), event.oldDirection(), event.newDirection());
    }
}
