package se.segersten.wreckage.game.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.Position;

class CorridorAsciiRendererTest {
    @Test void rendersActualTilesWithSeedAndDensity() {
        CorridorNetwork network = new CorridorNetwork(4, 3, 73L, CorridorGenerationParameters.defaults(),
                Set.of(new Position(0, 0), new Position(1, 0), new Position(1, 1), new Position(3, 2)),
                Set.of(), 1);

        assertThat(new CorridorAsciiRenderer().render(network)).isEqualTo(String.join(System.lineSeparator(),
                "seed=73 density=0.3333",
                "###.",
                "#.##",
                "..##"));
    }
}
