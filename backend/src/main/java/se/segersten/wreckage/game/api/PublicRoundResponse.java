package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record PublicRoundResponse(int number, RoundPhase phase, Map<UUID,Boolean> ready, List<VehicleResponse> initialVehicles, List<PlaybackStepResponse> playback) {
    static PublicRoundResponse from(Round r) {
        if(r==null)return null;
        var ready=r.programs().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().ready()));
        var playback=r.phase()==RoundPhase.PLAYBACK?r.playback().stream().map(PlaybackStepResponse::from).toList():List.<PlaybackStepResponse>of();
        return new PublicRoundResponse(r.number(),r.phase(),ready,r.initialState().vehicleStates().stream().map(VehicleResponse::from).toList(),playback);
    }
}
