package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.Set;

public record Board(int width, int height, Set<Position> walls) {

    public Board(int width, int height) {
        this(width, height, Set.of());
    }

    public Board {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
        walls = Set.copyOf(Objects.requireNonNull(walls));
        if (walls.stream().anyMatch(position -> position.x() < 0 || position.x() >= width
                || position.y() < 0 || position.y() >= height))
            throw new IllegalArgumentException("Walls must be inside the board");
    }

    public boolean isValidPosition(Position position) {
        return isWithinBounds(position);
    }

    public boolean blocksShot(Position position) { return walls.contains(position); }

    private boolean isWithinBounds(Position position) {
        Objects.requireNonNull(position);
        return position.x() >= 0 && position.x() < width && position.y() >= 0 && position.y() < height;
    }
}
