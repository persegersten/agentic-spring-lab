package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record PublicRoundResponse(int number, RoundPhase phase, java.time.Instant planningDeadline,
                                  Map<UUID,Boolean> ready, List<UUID> initiative,
                                  List<VehicleResponse> initialVehicles, Map<UUID,Integer> initialScores,
                                  List<RoundEventResponse> startEvents, List<RoundEventResponse> playback) {
    static PublicRoundResponse from(Round r) {
        if(r==null)return null;
        var ready=r.programs().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().ready()));
        var playback=r.phase()==RoundPhase.PLAYBACK?r.playback().stream().map(RoundEventResponse::from).toList():List.<RoundEventResponse>of();
        return new PublicRoundResponse(r.number(),r.phase(),r.planningDeadline(),ready,r.initiative(),r.initialState().vehicleStates().stream().map(VehicleResponse::from).toList(),r.initialScores(),r.startEvents().stream().map(RoundEventResponse::from).toList(),playback);
    }
}
