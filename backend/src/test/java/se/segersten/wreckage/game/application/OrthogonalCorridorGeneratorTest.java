package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.CorridorGenerationParameters;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Position;

class OrthogonalCorridorGeneratorTest {
    private final OrthogonalCorridorGenerator generator = new OrthogonalCorridorGenerator();
    private final CorridorGeometryValidator validator = new CorridorGeometryValidator();
    private final CorridorGenerationParameters defaults = CorridorGenerationParameters.defaults();

    @Test void generationIsDeterministic() {
        CorridorNetwork first = generator.generate(12, 12, 998_122L, defaults);
        CorridorNetwork second = generator.generate(12, 12, 998_122L, defaults);
        CorridorNetwork other = generator.generate(12, 12, 998_123L, defaults);

        assertThat(second).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
        assertThat(new CorridorAsciiFormatter().format(second, 998_122L))
                .isEqualTo(new CorridorAsciiFormatter().format(first, 998_122L));
    }

    @Test void oneHundredSeedsPerSupportedSizeSatisfyHardGeometryRules() {
        int[][] sizes = {{10, 10}, {12, 12}, {16, 16}};
        int withinSuggestedDensity = 0;
        int mapsWithCycles = 0;
        double totalDensity = 0;
        int generated = 0;

        for (int[] size : sizes) {
            for (long seed = 0; seed < 100; seed++) {
                String context = "seed=" + seed + ", size=" + size[0] + "x" + size[1]
                        + ", parameters=" + defaults;
                CorridorNetwork network = generator.generate(size[0], size[1], seed, defaults);
                assertThat(network.width()).as(context).isEqualTo(size[0]);
                assertThat(network.height()).as(context).isEqualTo(size[1]);
                assertThat(network.traversable()).as(context).allSatisfy(position -> {
                    assertThat(position.x()).isBetween(0, size[0] - 1);
                    assertThat(position.y()).isBetween(0, size[1] - 1);
                });
                assertThat(validator.isConnected(network)).as(context).isTrue();
                assertThat(validator.hasOpenThreeByThree(network)).as(context).isFalse();
                assertThat(validator.longestStraightRun(network)).as(context)
                        .isGreaterThanOrEqualTo(Math.min(defaults.preferredStraightLength(), Math.max(size[0], size[1])));
                validator.validate(network);

                if (network.density() >= 0.35 && network.density() <= 0.50) withinSuggestedDensity++;
                if (edgeCount(network) >= network.traversable().size()) mapsWithCycles++;
                totalDensity += network.density();
                generated++;
            }
        }

        assertThat(withinSuggestedDensity).isGreaterThanOrEqualTo(generated * 9 / 10);
        assertThat(totalDensity / generated).isBetween(0.35, 0.50);
        assertThat(mapsWithCycles).isGreaterThanOrEqualTo(generated * 3 / 4);
    }

    @Test void configuredDensityAndConnectionProbabilityInfluenceTheResult() {
        CorridorGenerationParameters sparse = new CorridorGenerationParameters(0.35, 6, 0.0);
        CorridorGenerationParameters dense = new CorridorGenerationParameters(0.49, 6, 0.8);
        CorridorNetwork sparseNetwork = generator.generate(16, 16, 42L, sparse);
        CorridorNetwork denseNetwork = generator.generate(16, 16, 42L, dense);

        assertThat(sparseNetwork.density()).isLessThan(denseNetwork.density());
        assertThat(sparseNetwork.density()).isGreaterThanOrEqualTo(0.35);
        assertThat(denseNetwork.density()).isGreaterThanOrEqualTo(0.49);
    }

    @Test void generationUsesBoundedAttemptsEvenWhenDensityCannotBeReached() {
        CorridorGenerationParameters impossibleWithoutARoom = new CorridorGenerationParameters(1.0, 20, 1.0);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            CorridorNetwork network = generator.generate(10, 10, 7L, impossibleWithoutARoom);
            validator.validate(network);
            assertThat(network.density()).isLessThan(1.0);
        });
        assertThat(OrthogonalCorridorGenerator.MAX_GENERATION_ATTEMPTS).isPositive();
    }

    @Test void validatesDimensionsAndParameters() {
        assertThatThrownBy(() -> generator.generate(0, 10, 1L, defaults))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dimensions");
        assertThatThrownBy(() -> generator.generate(10, -1, 1L, defaults))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dimensions");
        assertThatThrownBy(() -> generator.generate(10, 10, 1L, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("parameters");
        assertThatThrownBy(() -> new CorridorGenerationParameters(0.0, 6, 0.2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorridorGenerationParameters(Double.NaN, 6, 0.2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorridorGenerationParameters(0.4, 0, 0.2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorridorGenerationParameters(0.4, 6, -0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorridorGenerationParameters(0.4, 6, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private int edgeCount(CorridorNetwork network) {
        int edges = 0;
        Set<Position> open = network.traversable();
        for (Position position : open) {
            if (open.contains(new Position(position.x() + 1, position.y()))) edges++;
            if (open.contains(new Position(position.x(), position.y() + 1))) edges++;
        }
        return edges;
    }
}
