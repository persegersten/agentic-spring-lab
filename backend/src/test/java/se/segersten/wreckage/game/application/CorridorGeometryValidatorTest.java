package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;

class CorridorGeometryValidatorTest {
    private final CorridorGeometryValidator validator = new CorridorGeometryValidator();

    @Test void acceptsOpenTwoByTwoLongCorridorsAndIntersections() {
        Set<Position> positions = new HashSet<>();
        for (int x = 0; x < 7; x++) positions.add(new Position(x, 2));
        for (int y = 0; y < 6; y++) positions.add(new Position(3, y));
        positions.add(new Position(4, 3));
        positions.add(new Position(4, 2));
        CorridorNetwork network = new CorridorNetwork(7, 6, positions);

        validator.validate(network);

        assertThat(validator.hasOpenThreeByThree(network)).isFalse();
        assertThat(validator.longestStraightRun(network)).isEqualTo(7);
    }

    @Test void rejectsThreeByThreeAndLargerOpenAreas() {
        assertThatThrownBy(() -> validator.validate(rectangle(3, 3)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3x3");
        assertThatThrownBy(() -> validator.validate(rectangle(5, 4)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3x3");
    }

    @Test void requiresOrthogonalConnectivityAndRejectsDiagonalOnlyContact() {
        CorridorNetwork diagonal = new CorridorNetwork(2, 2,
                Set.of(new Position(0, 0), new Position(1, 1)));
        assertThat(validator.isConnected(diagonal)).isFalse();
        assertThatThrownBy(() -> validator.validate(diagonal)).hasMessageContaining("disconnected");

        CorridorNetwork horizontal = new CorridorNetwork(4, 1,
                Set.of(new Position(0, 0), new Position(1, 0), new Position(2, 0), new Position(3, 0)));
        CorridorNetwork vertical = new CorridorNetwork(1, 4,
                Set.of(new Position(0, 0), new Position(0, 1), new Position(0, 2), new Position(0, 3)));
        assertThat(validator.isConnected(horizontal)).isTrue();
        assertThat(validator.isConnected(vertical)).isTrue();
    }

    @Test void appliesWallsFromEitherSideOfAnEdgeLikeBoardMovement() {
        CorridorNetwork network = new CorridorNetwork(2, 1, Set.of(new Position(0, 0), new Position(1, 0)));
        assertThat(validator.isConnected(network, Set.of(new Wall(new Position(0, 0), Direction.EAST)))).isFalse();
        assertThat(validator.isConnected(network, Set.of(new Wall(new Position(1, 0), Direction.WEST)))).isFalse();
    }

    @Test void calculatesDensityFromTraversableTiles() {
        CorridorNetwork network = new CorridorNetwork(4, 2,
                Set.of(new Position(0, 0), new Position(1, 0), new Position(2, 0)));
        assertThat(network.density()).isEqualTo(3.0 / 8.0);
        assertThat(network.blockedPositionsInRowMajorOrder()).hasSize(5);
    }

    private CorridorNetwork rectangle(int width, int height) {
        Set<Position> positions = new HashSet<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) positions.add(new Position(x, y));
        return new CorridorNetwork(width, height, positions);
    }
}
