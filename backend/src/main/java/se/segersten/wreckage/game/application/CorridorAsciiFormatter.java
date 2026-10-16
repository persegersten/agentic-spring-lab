package se.segersten.wreckage.game.application;

import java.util.Locale;
import java.util.Objects;
import se.segersten.wreckage.game.domain.CorridorNetwork;
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
}
