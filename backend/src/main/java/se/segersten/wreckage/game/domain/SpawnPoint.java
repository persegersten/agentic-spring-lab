package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record SpawnPoint(Position position, Direction orientation) {
    public SpawnPoint {
        Objects.requireNonNull(position);
        Objects.requireNonNull(orientation);
    }
}
