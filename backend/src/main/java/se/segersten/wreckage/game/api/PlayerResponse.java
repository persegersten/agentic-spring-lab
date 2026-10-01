package se.segersten.wreckage.game.api;

import java.util.UUID;
import java.util.Set;

import se.segersten.wreckage.game.domain.Player;

public record PlayerResponse(UUID id, String name, int score, Set<String> visitedCheckpoints, int crashes) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getName(), player.getScore(), player.getVisitedCheckpoints(),
                player.getCrashes());
    }
}
