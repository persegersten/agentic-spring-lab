package se.segersten.wreckage.game.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.MapDefinition;
import se.segersten.wreckage.game.domain.Position;

public class CorridorAsciiFormatter {
    public String format(CorridorNetwork network, long seed) {
        Objects.requireNonNull(network, "Corridor network is required");
        StringBuilder result = new StringBuilder(String.format(Locale.ROOT,
                "seed=%d density=%.3f%n", seed, network.density()));
        for (int y = network.height() - 1; y >= 0; y--) {
            for (int x = 0; x < network.width(); x++)
                result.append(network.isTraversable(new Position(x, y)) ? '.' : '#');
            if (y > 0) result.append(System.lineSeparator());
        }
        return result.toString();
    }

    /** Formats a complete generated map. Checkpoints take precedence over starts in the unlikely event of overlap. */
    public String format(MapDefinition map, long seed) {
        Objects.requireNonNull(map, "Map definition is required");
        var obstacles = Set.copyOf(map.obstacles());
        var starts = map.starts().stream().map(start -> start.position()).collect(java.util.stream.Collectors.toSet());
        var checkpoints = map.checkpoints().stream().collect(java.util.stream.Collectors.toMap(
                checkpoint -> checkpoint.position(), checkpoint -> Character.forDigit(checkpoint.order(), 10)));
        int traversable = map.width() * map.height() - obstacles.size();
        double density = (double) traversable / (map.width() * map.height());
        StringBuilder result = new StringBuilder(String.format(Locale.ROOT,
                "seed=%d map=%s density=%.3f%n", seed, map.id(), density));
        for (int y = map.height() - 1; y >= 0; y--) {
            for (int x = 0; x < map.width(); x++) {
                Position position = new Position(x, y);
                result.append(checkpoints.containsKey(position) ? checkpoints.get(position)
                        : starts.contains(position) ? 'S' : obstacles.contains(position) ? '#' : '.');
            }
            if (y > 0) result.append(System.lineSeparator());
        }
        return result.toString();
    }
}
