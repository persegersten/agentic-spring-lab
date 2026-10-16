package se.segersten.wreckage.game.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/** A lightweight corridor grid. Blocked cells can later be mapped directly to map obstacles. */
public record CorridorNetwork(int width, int height, Set<Position> traversable) {
    public CorridorNetwork {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Corridor dimensions must be positive");
        traversable = Set.copyOf(Objects.requireNonNull(traversable, "Traversable positions are required"));
        if (traversable.stream().anyMatch(position -> position == null || !inside(position, width, height)))
            throw new IllegalArgumentException("Traversable positions must be inside the corridor grid");
    }

    public boolean isTraversable(Position position) {
        return traversable.contains(position);
    }

    public double density() {
        return (double) traversable.size() / (width * height);
    }

    public List<Position> positionsInRowMajorOrder() {
        return traversable.stream()
                .sorted(Comparator.comparingInt(Position::y).reversed().thenComparingInt(Position::x))
                .toList();
    }

    public List<Position> blockedPositionsInRowMajorOrder() {
        return IntStream.range(0, width * height)
                .mapToObj(index -> new Position(index % width, height - 1 - index / width))
                .filter(position -> !isTraversable(position))
                .toList();
    }

    public List<Position> orthogonalTraversableNeighbors(Position position) {
        Objects.requireNonNull(position);
        return java.util.Arrays.stream(Direction.values()).map(position::move)
                .filter(this::isTraversable).toList();
    }

    private static boolean inside(Position position, int width, int height) {
        return position.x() >= 0 && position.x() < width && position.y() >= 0 && position.y() < height;
    }
}
