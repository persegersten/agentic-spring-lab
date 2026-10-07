package se.segersten.wreckage.game.domain;

import java.util.List;
import java.util.Set;

public record MapDefinition(String id, String name, int width, int height, PlayerCountRange players,
                            List<SpawnPoint> starts, List<Checkpoint> checkpoints, List<Position> obstacles,
                            List<Wall> walls, List<Position> pits, List<Conveyor> conveyors,
                            List<Rotator> rotators, List<Position> controlPoints) {
    public MapDefinition {
        starts = starts == null ? List.of() : List.copyOf(starts);
        checkpoints = checkpoints == null ? List.of() : List.copyOf(checkpoints);
        obstacles = obstacles == null ? List.of() : List.copyOf(obstacles);
        walls = walls == null ? List.of() : List.copyOf(walls);
        pits = pits == null ? List.of() : List.copyOf(pits);
        conveyors = conveyors == null ? List.of() : List.copyOf(conveyors);
        rotators = rotators == null ? List.of() : List.copyOf(rotators);
        controlPoints = controlPoints == null ? List.of() : List.copyOf(controlPoints);
    }

    public Board toBoard() {
        return new Board(id, name, width, height, Set.copyOf(walls), Set.copyOf(pits), Set.copyOf(checkpoints),
                starts, conveyors, rotators, Set.copyOf(controlPoints), Set.copyOf(obstacles));
    }
}
