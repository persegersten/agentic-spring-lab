package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record Checkpoint(String id, Position position) {
    public Checkpoint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Checkpoint id must not be blank");
        id = id.trim();
        Objects.requireNonNull(position);
    }
}
