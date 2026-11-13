package se.segersten.wreckage.game.api;
import java.util.List;
import java.util.UUID;
import se.segersten.wreckage.game.domain.*;
public record PlayerGameResponse(UUID id, UUID playerId, UUID hostPlayerId, GameStatus status, int roundLimit,
        GameConfiguration configuration, List<PlayerResponse> players,
        BoardResponse board, List<VehicleResponse> vehicles, PlayerRoundResponse round,
        boolean shieldAvailable, List<GamePlacement> placements, ContinuousSnapshot continuous) {
    static PlayerGameResponse from(Game game, UUID playerId) {
        Round r=game.getRound(); PlayerRoundResponse round=null;
        if(r!=null){ PlayerProgram p=r.programs().get(playerId); boolean privatePlanning=r.phase()==RoundPhase.PLANNING&&p!=null; round=new PlayerRoundResponse(PublicRoundResponse.from(r),privatePlanning?p.hand():List.of(),privatePlanning?p.commands():List.of(),privatePlanning&&p.shieldSelected()); }
        return new PlayerGameResponse(game.getId(), playerId, game.getHostPlayerId(), game.getStatus(), game.getConfiguration().roundLimit(),
                game.getConfiguration(),
                game.getPlayers().stream().map(player -> PlayerResponse.from(player, game.getBoard())).toList(),
                BoardResponse.from(game.getBoard()), game.getVehicleStates().stream().map(VehicleResponse::from).toList(), round,
                game.isShieldAvailable(playerId),
                game.getPlacements(), snapshot(game, playerId));
    }
    private static MovementOrder lastExecutedCommand(Game game, UUID playerId) {
        if (game.getContinuous().tick == 0 || game.getRound() == null) return null;
        var program = game.getRound().programs().get(playerId);
        return program == null || program.commands().isEmpty() ? null : program.commands().getFirst();
    }
    private static ContinuousSnapshot snapshot(Game game, UUID playerId) {
        var c = game.getContinuous();
        if (c == null) return null;
        boolean running = game.getStatus() == GameStatus.RUNNING;
        var active = running ? game.getPlayers().stream().map(Player::getId)
                .filter(id -> c.shieldedPlayersAt(c.tick).contains(id) && c.shieldTicksRemaining(id) > 0)
                .filter(id -> game.getVehicleStates().stream().anyMatch(v -> v.vehicle().playerId().equals(id) && v.isActive()))
                .toList() : List.<UUID>of();
        return new ContinuousSnapshot(c.revision, c.tick, java.time.Instant.now(), c.nextTick, c.deadline,
                c.intervalMillis, c.queues.get(playerId).snapshot(), lastExecutedCommand(game, playerId),
                running && c.shieldPending(playerId), running ? c.shieldTicksRemaining(playerId) : 0, active);
    }
    public record ContinuousSnapshot(long revision, long tick, java.time.Instant serverTime, java.time.Instant nextTick,
            java.time.Instant deadline, long intervalMillis, List<MovementOrder> queue, MovementOrder lastExecutedCommand,
            boolean shieldPending, int shieldTicksRemaining, List<UUID> activeShieldPlayerIds) {}
    public record PlayerRoundResponse(PublicRoundResponse state, List<MovementOrder> hand, List<MovementOrder> program,
                                      boolean shieldSelected) {}
}
