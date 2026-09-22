package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import se.segersten.wreckage.game.domain.*;
public record PlayerGameResponse(UUID id, UUID playerId, GameStatus status,
        GameConfiguration configuration, Instant joinDeadline, List<PlayerResponse> players,
        BoardResponse board, List<VehicleResponse> vehicles, PlayerRoundResponse round) {
    static PlayerGameResponse from(Game game, UUID playerId) {
        Round r=game.getRound(); PlayerRoundResponse round=null;
        if(r!=null){ PlayerProgram p=r.programs().get(playerId); round=new PlayerRoundResponse(PublicRoundResponse.from(r),r.phase()==RoundPhase.PLANNING?(p.ready()?p.orders():p.hand()):List.of()); }
        return new PlayerGameResponse(game.getId(), playerId, game.getStatus(),
                game.getConfiguration(), game.getJoinDeadline(),
                game.getPlayers().stream().map(PlayerResponse::from).toList(),
                BoardResponse.from(game.getBoard()), game.getVehicleStates().stream().map(VehicleResponse::from).toList(), round);
    }
    public record PlayerRoundResponse(PublicRoundResponse state, List<MovementOrder> hand) {}
}
