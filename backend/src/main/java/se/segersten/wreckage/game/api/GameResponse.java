package se.segersten.wreckage.game.api;

import java.util.List;
import java.util.UUID;

import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.GameStatus;
import java.time.Instant;
import se.segersten.wreckage.game.domain.GamePlacement;

public record GameResponse(
        UUID id,
        GameStatus status,
        int roundLimit,
        GameConfiguration configuration,
        Instant createdAt,
        Instant joinDeadline,
        List<PlayerResponse> players,
        BoardResponse board,
        List<VehicleResponse> vehicles,
        PublicRoundResponse round,
        List<GamePlacement> placements) {

    public static GameResponse from(Game game) {
        List<PlayerResponse> players = game.getPlayers().stream()
                .map(PlayerResponse::from)
                .toList();
        BoardResponse board = game.getBoard() == null
                ? null
                : BoardResponse.from(game.getBoard());
        return new GameResponse(game.getId(), game.getStatus(), game.getConfiguration().roundLimit(), game.getConfiguration(),
                game.getCreatedAt(), game.getJoinDeadline(), players, board,
                game.getVehicleStates().stream().map(VehicleResponse::from).toList(),
                PublicRoundResponse.from(game.getRound()), game.getPlacements());
    }
}
