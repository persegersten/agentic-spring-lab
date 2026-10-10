package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record PlayerGameResponse(UUID id, UUID playerId, GameStatus status, int roundLimit,
        GameConfiguration configuration, List<PlayerResponse> players,
        BoardResponse board, List<VehicleResponse> vehicles, PlayerRoundResponse round,
        List<GamePlacement> placements, String shieldStatus) {
    static PlayerGameResponse from(Game game, UUID playerId) {
        Round r=game.getRound(); PlayerRoundResponse round=null;
        Player player = game.requirePlayer(playerId);
        String shieldStatus = player.isShieldConsumed() ? "CONSUMED" : "AVAILABLE";
        if(r!=null){ PlayerProgram p=r.programs().get(playerId); boolean privatePlanning=r.phase()==RoundPhase.PLANNING&&p!=null;
            if (p != null && p.shieldSelected()) shieldStatus = r.phase() == RoundPhase.PLANNING ? "SELECTED" : "ACTIVE";
            round=new PlayerRoundResponse(PublicRoundResponse.from(r),privatePlanning?p.hand():List.of(),privatePlanning?p.commands():List.of(),privatePlanning&&p.shieldSelected()); }
        return new PlayerGameResponse(game.getId(), playerId, game.getStatus(), game.getConfiguration().roundLimit(),
                game.getConfiguration(),
                game.getPlayers().stream().map(candidate -> PlayerResponse.from(candidate, game.getBoard())).toList(),
                BoardResponse.from(game.getBoard()), game.getVehicleStates().stream().map(VehicleResponse::from).toList(), round,
                game.getPlacements(), shieldStatus);
    }
    public record PlayerRoundResponse(PublicRoundResponse state, List<MovementOrder> hand, List<MovementOrder> program,
                                      boolean shieldSelected) {}
}
