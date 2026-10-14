package se.segersten.wreckage.game.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;

class CorridorNetworkGeneratorTest {
    private final CorridorNetworkGenerator generator = new CorridorNetworkGenerator();
    private final CorridorNetworkValidator validator = new CorridorNetworkValidator();

    @Test void generationIsDeterministic() {
        CorridorGenerationParameters parameters = CorridorGenerationParameters.defaults();
        CorridorNetwork first = generator.generate(12, 12, 8675309L, parameters);
        CorridorNetwork second = generator.generate(12, 12, 8675309L, parameters);

        assertThat(second).isEqualTo(first);
        assertThat(new CorridorAsciiRenderer().render(second))
                .isEqualTo(new CorridorAsciiRenderer().render(first));
    }

    @Test void generatesValidNetworksForThreeHundredSeedAndSizeCombinations() {
        CorridorGenerationParameters parameters = CorridorGenerationParameters.defaults();
        int[] sizes = {10, 12, 16};
        for (int size : sizes) {
            for (long seed = 0; seed < 100; seed++) {
                CorridorNetwork network = generator.generate(size, size, seed, parameters);
                String context = "seed=" + seed + ", size=" + size + ", parameters=" + parameters;
                assertThat(network.width()).as(context).isEqualTo(size);
                assertThat(network.height()).as(context).isEqualTo(size);
                assertThat(network.attemptsUsed()).as(context).isBetween(1, parameters.maxGenerationAttempts());
                assertThat(network.traversablePositions()).as(context).isNotEmpty();
                assertThat(network.density()).as(context).isBetween(0.35, 0.50);
                assertThat(independentReachableCount(network)).as(context)
                        .isEqualTo(network.traversablePositions().size());
                assertThat(validator.containsOpenThreeByThree(size, size, network.traversablePositions()))
                        .as(context).isFalse();
                assertThat(longestHorizontalRun(network)).as(context).isGreaterThanOrEqualTo(size / 2);
                assertThat(longestVerticalRun(network)).as(context).isGreaterThanOrEqualTo(size / 2);
                validator.validate(network);
            }
        }
    }

    @Test void computesDensityAndComplementaryObstacles() {
        CorridorNetwork network = generator.generate(10, 10, 1234L);

        assertThat(network.density()).isEqualTo(network.traversablePositions().size() / 100.0);
        assertThat(network.obstacles()).hasSize(100 - network.traversablePositions().size());
        assertThat(network.obstacles()).doesNotContainAnyElementsOf(network.traversablePositions());
    }

    @Test void configuredDensityInfluencesGeneratedDensity() {
        CorridorGenerationParameters sparse = new CorridorGenerationParameters(0.30, 6, 0.2, 16);
        CorridorGenerationParameters dense = new CorridorGenerationParameters(0.48, 6, 0.2, 16);
        double sparseTotal = 0.0;
        double denseTotal = 0.0;
        for (long seed = 100; seed < 120; seed++) {
            sparseTotal += generator.generate(16, 16, seed, sparse).density();
            denseTotal += generator.generate(16, 16, seed, dense).density();
        }
        assertThat(denseTotal).isGreaterThan(sparseTotal);
    }

    @Test void additionalConnectionsProduceAlternativeRoutes() {
        CorridorGenerationParameters parameters = new CorridorGenerationParameters(0.45, 6, 1.0, 12);
        CorridorNetwork network = generator.generate(12, 12, 42L, parameters);

        assertThat(edgeCount(network)).isGreaterThanOrEqualTo(network.traversablePositions().size());
        validator.validate(network);
    }

    @Test void generationAttemptsAreBounded() {
        CorridorGenerationParameters parameters = new CorridorGenerationParameters(0.41, 20, 0.0, 1);
        CorridorNetwork network = generator.generate(10, 10, 99L, parameters);
        assertThat(network.attemptsUsed()).isEqualTo(1);
    }

    @Test void rejectsInvalidGenerationInputs() {
        assertThatThrownBy(() -> generator.generate(2, 10, 1L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 3x3");
        assertThatThrownBy(() -> generator.generate(10, 2, 1L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 3x3");
        assertThatThrownBy(() -> generator.generate(10, 10, 1L, null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parameters");
    }

    @Test void rejectsInvalidParameters() {
        assertInvalidParameters(Double.NaN, 5, 0.2, 2);
        assertInvalidParameters(0.0, 5, 0.2, 2);
        assertInvalidParameters(1.0, 5, 0.2, 2);
        assertInvalidParameters(0.4, 1, 0.2, 2);
        assertInvalidParameters(0.4, 5, -0.1, 2);
        assertInvalidParameters(0.4, 5, 1.1, 2);
        assertInvalidParameters(0.4, 5, 0.2, 0);
    }

    private void assertInvalidParameters(double density, int length, double connectionProbability, int attempts) {
        assertThatThrownBy(() -> new CorridorGenerationParameters(density, length, connectionProbability, attempts))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private int independentReachableCount(CorridorNetwork network) {
        Position start = network.traversablePositions().iterator().next();
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            for (Direction direction : Direction.values()) {
                Position next = current.move(direction);
                if (network.isTraversable(next) && seen.add(next)) queue.add(next);
            }
        }
        return seen.size();
    }

    private int longestHorizontalRun(CorridorNetwork network) {
        int longest = 0;
        for (int y = 0; y < network.height(); y++) {
            int run = 0;
            for (int x = 0; x < network.width(); x++) {
                run = network.isTraversable(new Position(x, y)) ? run + 1 : 0;
                longest = Math.max(longest, run);
            }
        }
        return longest;
    }

    private int longestVerticalRun(CorridorNetwork network) {
        int longest = 0;
        for (int x = 0; x < network.width(); x++) {
            int run = 0;
            for (int y = 0; y < network.height(); y++) {
                run = network.isTraversable(new Position(x, y)) ? run + 1 : 0;
                longest = Math.max(longest, run);
            }
        }
        return longest;
    }

    private int edgeCount(CorridorNetwork network) {
        int edges = 0;
        for (Position position : network.traversablePositions()) {
            if (network.isTraversable(position.move(Direction.EAST))) edges++;
            if (network.isTraversable(position.move(Direction.NORTH))) edges++;
        }
        return edges;
    }
}
