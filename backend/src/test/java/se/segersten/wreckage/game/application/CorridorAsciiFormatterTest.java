package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Checkpoint;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.MapDefinition;
import se.segersten.wreckage.game.domain.PlayerCountRange;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.SpawnPoint;
import java.util.List;

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

    @Test void printsCompleteMapSeedStartsCheckpointsAndObstacles() {
        MapDefinition map = new MapDefinition("generated-2-test", "Generated", 4, 3,
                new PlayerCountRange(2, 2),
                List.of(new SpawnPoint(new Position(0, 0), Direction.NORTH),
                        new SpawnPoint(new Position(1, 0), Direction.NORTH)),
                List.of(new Checkpoint("CP1", 1, new Position(0, 2)),
                        new Checkpoint("CP2", 2, new Position(1, 2)),
                        new Checkpoint("CP3", 3, new Position(2, 2)),
                        new Checkpoint("CP4", 4, new Position(3, 2))),
                List.of(new Position(2, 1), new Position(3, 1)), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(new CorridorAsciiFormatter().format(map, 731L)).isEqualTo("""
                seed=731 map=generated-2-test density=0.833
                1234
                ..##
                SS..""");
    }
}
