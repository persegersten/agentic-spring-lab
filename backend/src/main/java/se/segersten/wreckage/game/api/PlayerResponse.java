package se.segersten.wreckage.game.api;

import java.util.UUID;

import se.segersten.wreckage.game.domain.Player;

public record PlayerResponse(UUID id, String name) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getName());
    }
}
