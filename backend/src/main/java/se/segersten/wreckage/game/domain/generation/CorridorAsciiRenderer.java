package se.segersten.wreckage.game.domain.generation;

import java.util.Locale;
import java.util.Objects;
import se.segersten.wreckage.game.domain.Position;

/** Developer diagnostic renderer. Rows are printed north-to-south, with (0, 0) at the bottom left. */
public final class CorridorAsciiRenderer {
    public String render(CorridorNetwork network) {
        Objects.requireNonNull(network, "Corridor network is required");
        StringBuilder result = new StringBuilder(String.format(Locale.ROOT,
                "seed=%d density=%.4f%n", network.seed(), network.density()));
        for (int y = network.height() - 1; y >= 0; y--) {
            for (int x = 0; x < network.width(); x++) {
                result.append(network.isTraversable(new Position(x, y)) ? '.' : '#');
            }
            if (y > 0) result.append(System.lineSeparator());
        }
        return result.toString();
    }
}
