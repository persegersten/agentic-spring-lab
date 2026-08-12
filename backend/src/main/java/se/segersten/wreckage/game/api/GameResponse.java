package se.segersten.wreckage.game.api;

import java.util.List;
import java.util.UUID;

import se.segersten.wreckage.game.domain.Game;

public record GameResponse(
        UUID id,
        List<PlayerResponse> players,
        BoardResponse board) {

    public static GameResponse from(Game game) {
        List<PlayerResponse> players = game.getPlayers().stream()
                .map(PlayerResponse::from)
                .toList();
        BoardResponse board = game.getBoard() == null
                ? null
                : BoardResponse.from(game.getBoard());
        return new GameResponse(game.getId(), players, board);
    }
}
