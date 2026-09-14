package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;

public class Player {

    private final UUID id;
    private final String name;
    private final String accessTokenHash;

    Player(UUID id, String name, String accessTokenHash) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.name = name.trim();
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "accessTokenHash must not be null");
    }

    public static Player create(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAccessTokenHash() { return accessTokenHash; }
}
