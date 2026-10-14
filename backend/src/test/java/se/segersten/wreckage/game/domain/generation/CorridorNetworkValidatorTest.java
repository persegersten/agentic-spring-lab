package se.segersten.wreckage.game.domain.generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;

class CorridorNetworkValidatorTest {
    private final CorridorNetworkValidator validator = new CorridorNetworkValidator();

    @Test void acceptsOpenTwoByTwoIntersection() {
        validator.validate(network(4, 4, Set.of(
                p(1, 1), p(2, 1), p(1, 2), p(2, 2))));
    }

    @Test void rejectsOpenThreeByThreeAndLargerRooms() {
        assertThatThrownBy(() -> validator.validate(network(3, 3, rectangle(0, 0, 3, 3))))
                .hasMessageContaining("3x3");
        assertThatThrownBy(() -> validator.validate(network(5, 5, rectangle(0, 0, 4, 4))))
                .hasMessageContaining("3x3");
    }

    @Test void acceptsLongHorizontalAndVerticalCorridors() {
        validator.validate(network(8, 3, rectangle(0, 1, 8, 1)));
        validator.validate(network(3, 8, rectangle(1, 0, 1, 8)));
    }

    @Test void rejectsDiagonalOnlyAndIsolatedConnections() {
        assertThatThrownBy(() -> validator.validate(network(3, 3, Set.of(p(0, 0), p(1, 1), p(2, 2)))))
                .hasMessageContaining("disconnected");
        assertThatThrownBy(() -> validator.validate(network(4, 3, Set.of(p(0, 1), p(1, 1), p(3, 1)))))
                .hasMessageContaining("disconnected");
    }

    @Test void edgeWallsBlockInBothRepresentations() {
        Set<Position> line = Set.of(p(0, 1), p(1, 1));
        validator.validate(3, 3, line, Set.of(), Set.of());
        assertThatThrownBy(() -> validator.validate(3, 3, line, Set.of(),
                Set.of(new Wall(p(0, 1), Direction.EAST))))
                .hasMessageContaining("disconnected");
        assertThatThrownBy(() -> validator.validate(3, 3, line, Set.of(),
                Set.of(new Wall(p(1, 1), Direction.WEST))))
                .hasMessageContaining("disconnected");
    }

    @Test void pitsAreNotTraversableForConnectivity() {
        Set<Position> line = Set.of(p(0, 1), p(1, 1), p(2, 1));
        assertThatThrownBy(() -> validator.validate(3, 3, line, Set.of(p(1, 1)), Set.of()))
                .hasMessageContaining("disconnected");
    }

    private CorridorNetwork network(int width, int height, Set<Position> positions) {
        return new CorridorNetwork(width, height, 1L, CorridorGenerationParameters.defaults(),
                positions, Set.of(), 1);
    }

    private Set<Position> rectangle(int x, int y, int width, int height) {
        Set<Position> result = new LinkedHashSet<>();
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) result.add(p(x + dx, y + dy));
        }
        return result;
    }

    private Position p(int x, int y) { return new Position(x, y); }
}
