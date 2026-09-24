package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.Set;

public record Board(int width, int height, Set<Position> walls, Set<Position> pits) {

    public Board(int width, int height) {
        this(width, height, Set.of(), Set.of());
    }

    public Board(int width, int height, Set<Position> walls) {
        this(width, height, walls, Set.of());
    }

    public Board {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
        walls = Set.copyOf(Objects.requireNonNull(walls));
        pits = Set.copyOf(Objects.requireNonNull(pits));
        if (walls.stream().anyMatch(position -> !isWithinBounds(position, width, height)))
            throw new IllegalArgumentException("Walls must be inside the board");
        if (pits.stream().anyMatch(position -> !isWithinBounds(position, width, height)))
            throw new IllegalArgumentException("Pits must be inside the board");
    }

    public boolean isValidPosition(Position position) {
        return isWithinBounds(position);
    }

    public boolean blocksShot(Position position) { return walls.contains(position); }

    public boolean isPit(Position position) { return pits.contains(position); }

    private boolean isWithinBounds(Position position) {
        return isWithinBounds(position, width, height);
    }

    private static boolean isWithinBounds(Position position, int width, int height) {
        Objects.requireNonNull(position);
        return position.x() >= 0 && position.x() < width && position.y() >= 0 && position.y() < height;
    }
}
