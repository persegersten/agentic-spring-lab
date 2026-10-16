package se.segersten.wreckage.game.application;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;

public class CorridorGeometryValidator {
    public void validate(CorridorNetwork network) {
        validate(network, Set.of());
    }

    public void validate(CorridorNetwork network, Set<Wall> walls) {
        Objects.requireNonNull(network, "Corridor network is required");
        walls = Set.copyOf(Objects.requireNonNull(walls, "Walls are required"));
        if (network.traversable().isEmpty()) throw new IllegalArgumentException("Corridor network must not be empty");
        if (hasOpenThreeByThree(network))
            throw new IllegalArgumentException("Corridor network contains a fully open 3x3 area");
        int reachable = reachableCount(network, walls);
        if (reachable != network.traversable().size())
            throw new IllegalArgumentException("Corridor network is disconnected: reached " + reachable
                    + " of " + network.traversable().size() + " traversable tiles");
    }

    public boolean isConnected(CorridorNetwork network) {
        return isConnected(network, Set.of());
    }

    public boolean isConnected(CorridorNetwork network, Set<Wall> walls) {
        if (network == null || network.traversable().isEmpty()) return false;
        return reachableCount(network, Set.copyOf(walls)) == network.traversable().size();
    }

    public boolean hasOpenThreeByThree(CorridorNetwork network) {
        Objects.requireNonNull(network);
        for (int y = 0; y <= network.height() - 3; y++) {
            for (int x = 0; x <= network.width() - 3; x++) {
                boolean open = true;
                for (int dy = 0; dy < 3 && open; dy++)
                    for (int dx = 0; dx < 3; dx++)
                        if (!network.isTraversable(new Position(x + dx, y + dy))) { open = false; break; }
                if (open) return true;
            }
        }
        return false;
    }

    public int longestStraightRun(CorridorNetwork network) {
        Objects.requireNonNull(network);
        int longest = 0;
        for (int y = 0; y < network.height(); y++) {
            int run = 0;
            for (int x = 0; x < network.width(); x++) {
                run = network.isTraversable(new Position(x, y)) ? run + 1 : 0;
                longest = Math.max(longest, run);
            }
        }
        for (int x = 0; x < network.width(); x++) {
            int run = 0;
            for (int y = 0; y < network.height(); y++) {
                run = network.isTraversable(new Position(x, y)) ? run + 1 : 0;
                longest = Math.max(longest, run);
            }
        }
        return longest;
    }

    private int reachableCount(CorridorNetwork network, Set<Wall> walls) {
        Position first = network.positionsInRowMajorOrder().getFirst();
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        seen.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            for (Direction direction : Direction.values()) {
                Position next = current.move(direction);
                if (!network.isTraversable(next) || hasWall(walls, current, direction) || !seen.add(next)) continue;
                queue.add(next);
            }
        }
        return seen.size();
    }

    private boolean hasWall(Set<Wall> walls, Position cell, Direction direction) {
        return walls.contains(new Wall(cell, direction))
                || walls.contains(new Wall(cell.move(direction), direction.reverse()));
    }
}
