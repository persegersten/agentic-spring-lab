package se.segersten.wreckage.game.application;

import java.util.UUID;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(UUID id) {
        super("Game not found: " + id);
    }
}
