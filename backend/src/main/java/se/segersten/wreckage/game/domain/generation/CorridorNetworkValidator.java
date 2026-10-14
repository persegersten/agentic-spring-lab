package se.segersten.wreckage.game.domain.generation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;

/** Independent hard-constraint validation for generated and handcrafted corridor geometry. */
public final class CorridorNetworkValidator {
    public void validate(CorridorNetwork network) {
        Objects.requireNonNull(network, "Corridor network is required");
        validate(network.width(), network.height(), network.traversablePositions(), Set.of(), network.walls());
    }

    public void validate(int width, int height, Set<Position> traversable, Set<Position> pits, Set<Wall> walls) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Corridor dimensions must be positive");
        Objects.requireNonNull(traversable, "Traversable positions are required");
        Objects.requireNonNull(pits, "Pits are required");
        Objects.requireNonNull(walls, "Walls are required");
        if (traversable.isEmpty()) throw new IllegalArgumentException("Corridor network must contain traversable tiles");
        for (Position position : traversable) requireInside(position, width, height, "Traversable position");
        for (Position pit : pits) requireInside(pit, width, height, "Pit");
        for (Wall wall : walls) requireInside(wall.cell(), width, height, "Wall cell");
        if (containsOpenThreeByThree(width, height, traversable, pits)) {
            throw new IllegalArgumentException("Corridor network contains a fully open 3x3 area");
        }
        int reachable = reachableCount(width, height, traversable, pits, walls);
        int expected = (int) traversable.stream().filter(position -> !pits.contains(position)).count();
        if (expected == 0) throw new IllegalArgumentException("Corridor network must contain non-pit traversable tiles");
        if (reachable != expected) {
            throw new IllegalArgumentException("Corridor network is disconnected: reached " + reachable
                    + " of " + expected + " traversable tiles");
        }
    }

    public boolean containsOpenThreeByThree(int width, int height, Set<Position> traversable) {
        return containsOpenThreeByThree(width, height, traversable, Set.of());
    }

    private boolean containsOpenThreeByThree(int width, int height, Set<Position> traversable, Set<Position> pits) {
        for (int y = 0; y <= height - 3; y++) {
            for (int x = 0; x <= width - 3; x++) {
                boolean open = true;
                for (int dy = 0; dy < 3 && open; dy++) {
                    for (int dx = 0; dx < 3; dx++) {
                        Position position = new Position(x + dx, y + dy);
                        if (!traversable.contains(position) || pits.contains(position)) {
                            open = false;
                            break;
                        }
                    }
                }
                if (open) return true;
            }
        }
        return false;
    }

    private int reachableCount(int width, int height, Set<Position> traversable, Set<Position> pits, Set<Wall> walls) {
        Position start = null;
        for (int y = 0; y < height && start == null; y++) {
            for (int x = 0; x < width; x++) {
                Position candidate = new Position(x, y);
                if (traversable.contains(candidate) && !pits.contains(candidate)) {
                    start = candidate;
                    break;
                }
            }
        }
        if (start == null) return 0;
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            for (Direction direction : Direction.values()) {
                Position next = current.move(direction);
                if (!inside(next, width, height) || !traversable.contains(next) || pits.contains(next)
                        || hasWall(walls, current, direction) || !seen.add(next)) continue;
                queue.add(next);
            }
        }
        return seen.size();
    }

    private boolean hasWall(Set<Wall> walls, Position cell, Direction direction) {
        return walls.contains(new Wall(cell, direction))
                || walls.contains(new Wall(cell.move(direction), direction.reverse()));
    }

    private void requireInside(Position position, int width, int height, String kind) {
        if (!inside(position, width, height)) throw new IllegalArgumentException(kind + " outside board: " + position);
    }

    private boolean inside(Position position, int width, int height) {
        return position != null && position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height;
    }
}
