package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record PlaybackStepResponse(int index, Map<UUID,MovementOrder> commands, List<VehicleResponse> vehicles) {
    static PlaybackStepResponse from(PlaybackStep s) { return new PlaybackStepResponse(s.index(),s.commands(),s.vehicleStates().stream().map(VehicleResponse::from).toList()); }
}
