package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Position;

class CorridorAsciiFormatterTest {
    @Test void printsMetadataAndTheActualGrid() {
        CorridorNetwork network = new CorridorNetwork(4, 3,
                Set.of(new Position(0, 2), new Position(1, 2), new Position(1, 1), new Position(1, 0)));

        String output = new CorridorAsciiFormatter().format(network, 731L);

        assertThat(output).isEqualTo("""
                seed=731 density=0.333
                ..##
                #.##
                #.##""");
    }
}
