package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.*;

class MapValidatorTest {
    private final MapValidator validator = new MapValidator();

    @Test void rejectsCoordinatesDuplicatesAndOverlaps() {
        assertInvalid(withCheckpoints(List.of(cp(1, 1, 1), cp(2, 5, 1), cp(3, 3, 3), cp(4, 1, 3))), "outside board");
        assertInvalid(map(starts(new Position(-1, 0), new Position(3, 0)), checkpoints(), List.of()), "start outside");
        assertInvalid(map(starts(new Position(0, 0), new Position(0, 0)), checkpoints(), List.of()), "duplicate start");
        assertInvalid(map(starts(), checkpoints(), List.of(new Position(6, 6))), "obstacle outside");
        assertInvalid(map(starts(), checkpoints(), List.of(new Position(0, 0))), "overlaps start");
        assertInvalid(map(starts(), checkpoints(), List.of(new Position(2, 1))), "overlaps checkpoint");
        assertInvalid(map(starts(), checkpoints(), List.of(new Position(4, 4), new Position(4, 4))), "duplicate obstacle");
    }

    @Test void rejectsMissingDuplicateAndUnreachableCheckpoints() {
        assertInvalid(withCheckpoints(checkpoints().subList(0, 3)), "exactly four");
        assertInvalid(withCheckpoints(List.of(cp(1,2,1), cp(1,3,1), cp(3,3,3), cp(4,1,3))), "CP1");
        MapDefinition base = map(starts(), checkpoints(), List.of());
        Position target = new Position(2, 1);
        MapDefinition sealed = new MapDefinition(base.id(), base.name(), base.width(), base.height(), base.players(),
                base.starts(), base.checkpoints(), base.obstacles(),
                java.util.Arrays.stream(Direction.values()).map(direction -> new Wall(target, direction)).toList(),
                base.pits(), base.conveyors(), base.rotators(), base.controlPoints());
        assertInvalid(sealed, "unreachable");
    }

    private void assertInvalid(MapDefinition map, String text) {
        assertThatThrownBy(() -> validator.validate(map)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(text);
    }
    private MapDefinition withCheckpoints(List<Checkpoint> cps) { return map(starts(), cps, List.of()); }
    private MapDefinition map(List<SpawnPoint> starts, List<Checkpoint> cps, List<Position> obstacles) {
        return new MapDefinition("test", "Test", 5, 5, new PlayerCountRange(2, 2), starts, cps, obstacles,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
    private List<SpawnPoint> starts(Position... values) {
        if (values.length == 0) values = new Position[]{new Position(0,0), new Position(4,0)};
        return java.util.Arrays.stream(values).map(p -> new SpawnPoint(p, Direction.NORTH)).toList();
    }
    private List<Checkpoint> checkpoints() { return List.of(cp(1,2,1), cp(2,1,3), cp(3,3,3), cp(4,2,2)); }
    private Checkpoint cp(int order, int x, int y) { return new Checkpoint("CP" + order, order, new Position(x,y)); }
}
