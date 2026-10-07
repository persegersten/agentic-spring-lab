package se.segersten.wreckage.game.application;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.segersten.wreckage.game.domain.*;

@Component
public class MapValidator {
    public void validate(MapDefinition map) {
        if (map == null) throw new IllegalArgumentException("Map definition is required");
        String prefix = "Invalid map '" + map.id() + "': ";
        if (map.id() == null || map.id().isBlank()) fail(prefix, "id must not be blank");
        if (map.name() == null || map.name().isBlank()) fail(prefix, "name must not be blank");
        if (map.width() <= 0 || map.height() <= 0) fail(prefix, "dimensions must be positive");
        if (map.players() == null) fail(prefix, "player range is required");
        if (map.starts().size() < map.players().max()) fail(prefix, "needs at least " + map.players().max() + " starts");
        coordinates(prefix, "start", map.starts().stream().map(SpawnPoint::position).toList(), map, true);
        coordinates(prefix, "obstacle", map.obstacles(), map, true);
        coordinates(prefix, "pit", map.pits(), map, true);
        coordinates(prefix, "control point", map.controlPoints(), map, true);
        coordinates(prefix, "checkpoint", map.checkpoints().stream().map(Checkpoint::position).toList(), map, true);
        coordinates(prefix, "conveyor", map.conveyors().stream().map(Conveyor::position).toList(), map, true);
        coordinates(prefix, "rotator", map.rotators().stream().map(Rotator::position).toList(), map, true);
        for (Wall wall : map.walls()) inside(prefix, "wall", wall.cell(), map);
        if (map.checkpoints().size() != 4) fail(prefix, "exactly four checkpoints are required");
        for (int order = 1; order <= 4; order++) {
            int expected = order;
            List<Checkpoint> found = map.checkpoints().stream()
                    .filter(cp -> cp.order() == expected && cp.id().equals("CP" + expected)).toList();
            if (found.size() != 1) fail(prefix, "checkpoint CP" + order + " must occur exactly once");
        }
        Set<Position> obstacles = Set.copyOf(map.obstacles());
        Set<Position> pits = Set.copyOf(map.pits());
        for (SpawnPoint start : map.starts()) if (obstacles.contains(start.position()))
            fail(prefix, "obstacle overlaps start at " + start.position());
        for (SpawnPoint start : map.starts()) if (pits.contains(start.position()))
            fail(prefix, "pit overlaps start at " + start.position());
        for (Checkpoint cp : map.checkpoints()) if (obstacles.contains(cp.position()))
            fail(prefix, "obstacle overlaps checkpoint " + cp.id());
        for (Checkpoint cp : map.checkpoints()) if (pits.contains(cp.position()))
            fail(prefix, "pit overlaps checkpoint " + cp.id());
        for (SpawnPoint start : map.starts()) requirePath(prefix, map, start.position(), checkpoint(map, 1).position(), "start to CP1");
        for (int order = 1; order < 4; order++)
            requirePath(prefix, map, checkpoint(map, order).position(), checkpoint(map, order + 1).position(),
                    "CP" + order + " to CP" + (order + 1));
    }

    private void coordinates(String prefix, String kind, List<Position> positions, MapDefinition map, boolean unique) {
        for (Position position : positions) inside(prefix, kind, position, map);
        if (unique && new HashSet<>(positions).size() != positions.size()) fail(prefix, "duplicate " + kind + " position");
    }
    private void inside(String prefix, String kind, Position p, MapDefinition map) {
        if (p == null || p.x() < 0 || p.x() >= map.width() || p.y() < 0 || p.y() >= map.height())
            fail(prefix, kind + " outside board: " + p);
    }
    private Checkpoint checkpoint(MapDefinition map, int order) {
        return map.checkpoints().stream().filter(cp -> cp.order() == order).findFirst().orElseThrow();
    }
    private void requirePath(String prefix, MapDefinition map, Position from, Position to, String label) {
        Set<Position> blocked = new HashSet<>(map.obstacles()); blocked.addAll(map.pits());
        Set<Position> seen = new HashSet<>(); ArrayDeque<Position> queue = new ArrayDeque<>();
        seen.add(from); queue.add(from);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            if (current.equals(to)) return;
            for (Direction direction : Direction.values()) {
                Position next = current.move(direction);
                if (next.x() < 0 || next.x() >= map.width() || next.y() < 0 || next.y() >= map.height()
                        || blocked.contains(next) || hasWall(map, current, direction) || !seen.add(next)) continue;
                queue.add(next);
            }
        }
        fail(prefix, label + " is unreachable");
    }
    private boolean hasWall(MapDefinition map, Position cell, Direction direction) {
        return map.walls().contains(new Wall(cell, direction))
                || map.walls().contains(new Wall(cell.move(direction), direction.reverse()));
    }
    private void fail(String prefix, String message) { throw new IllegalArgumentException(prefix + message); }
}
