package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record PlayerGameResponse(UUID id, UUID playerId, GameStatus status, int roundLimit,
        GameConfiguration configuration, List<PlayerResponse> players,
        BoardResponse board, List<VehicleResponse> vehicles, PlayerRoundResponse round,
        List<GamePlacement> placements) {
    static PlayerGameResponse from(Game game, UUID playerId) {
        Round r=game.getRound(); PlayerRoundResponse round=null;
        if(r!=null){ PlayerProgram p=r.programs().get(playerId); boolean privatePlanning=r.phase()==RoundPhase.PLANNING&&p!=null; round=new PlayerRoundResponse(PublicRoundResponse.from(r),privatePlanning?p.commands():List.of(),privatePlanning?p.scheduledAction():null); }
        return new PlayerGameResponse(game.getId(), playerId, game.getStatus(), game.getConfiguration().roundLimit(),
                game.getConfiguration(),
                game.getPlayers().stream().map(player -> PlayerResponse.from(player, game.getBoard())).toList(),
                BoardResponse.from(game.getBoard()), game.getVehicleStates().stream().map(VehicleResponse::from).toList(), round,
                game.getPlacements());
    }
    public record PlayerRoundResponse(PublicRoundResponse state, List<MovementOrder> program,
                                      ScheduledAction scheduledAction) {}
}
