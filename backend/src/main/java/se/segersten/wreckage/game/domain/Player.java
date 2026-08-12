package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public class Player {

    private final UUID id;
    private final String name;

    Player(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.name = name.trim();
    }

    public static Player rehydrate(UUID id, String name) {
        return new Player(id, name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
