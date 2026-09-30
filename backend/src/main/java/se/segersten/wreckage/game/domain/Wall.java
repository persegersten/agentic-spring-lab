package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record Wall(Position cell, Direction direction) {
    public Wall {
        Objects.requireNonNull(cell);
        Objects.requireNonNull(direction);
    }
}
