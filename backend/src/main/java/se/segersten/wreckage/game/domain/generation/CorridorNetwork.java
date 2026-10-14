package se.segersten.wreckage.game.domain.generation;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;

/**
 * Intermediate generated geometry. Blocked cells are deliberately represented as ordinary map obstacles,
 * making this result straightforward to transfer into a MapDefinition in a later feature.
 */
public record CorridorNetwork(int width, int height, long seed, CorridorGenerationParameters parameters,
                              Set<Position> traversablePositions, Set<Wall> walls, int attemptsUsed) {
    public CorridorNetwork {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Corridor dimensions must be positive");
        Objects.requireNonNull(parameters, "Generation parameters are required");
        traversablePositions = Set.copyOf(Objects.requireNonNull(traversablePositions, "Traversable positions are required"));
        walls = Set.copyOf(Objects.requireNonNull(walls, "Walls are required"));
        if (attemptsUsed <= 0 || attemptsUsed > parameters.maxGenerationAttempts()) {
            throw new IllegalArgumentException("Attempts used must be within the configured bound");
        }
        for (Position position : traversablePositions) requireInside(position, width, height, "Traversable position");
        for (Wall wall : walls) requireInside(wall.cell(), width, height, "Wall cell");
    }

    public boolean isTraversable(Position position) {
        return traversablePositions.contains(position);
    }

    public double density() {
        return traversablePositions.size() / (double) (width * height);
    }

    public Set<Position> obstacles() {
        Set<Position> result = new LinkedHashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Position position = new Position(x, y);
                if (!isTraversable(position)) result.add(position);
            }
        }
        return Set.copyOf(result);
    }

    private static void requireInside(Position position, int width, int height, String kind) {
        if (position == null || position.x() < 0 || position.x() >= width
                || position.y() < 0 || position.y() >= height) {
            throw new IllegalArgumentException(kind + " must be inside the corridor network: " + position);
        }
    }
}
