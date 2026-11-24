package se.segersten.wreckage.game.api;

import java.util.UUID;
import java.util.Set;

import se.segersten.wreckage.game.domain.Player;

public record PlayerResponse(UUID id, String name, int score, Set<String> visitedCheckpoints,
                             int capturedCheckpoints, String nextCheckpoint, int crashes, boolean automated) {

    public static PlayerResponse from(Player player, se.segersten.wreckage.game.domain.Board board) {
        return from(player, board, java.util.List.of(player));
    }

    public static PlayerResponse from(Player player, se.segersten.wreckage.game.domain.Board board,
                                      java.util.List<Player> players) {
        var next = se.segersten.wreckage.game.domain.FlagProgress.nextAvailable(board, players);
        return new PlayerResponse(player.getId(), player.getName(), player.getScore(), player.getVisitedCheckpoints(),
                player.getCapturedCheckpointCount(board), next == null ? null : next.id(), player.getCrashes(),
                player.isAutomated());
    }
}
