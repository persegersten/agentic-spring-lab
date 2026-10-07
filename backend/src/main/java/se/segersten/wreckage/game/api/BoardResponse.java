package se.segersten.wreckage.game.api;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;
import se.segersten.wreckage.game.domain.Checkpoint;
import se.segersten.wreckage.game.domain.Conveyor;import se.segersten.wreckage.game.domain.Rotator;
import java.util.Set;
import java.util.List;
import se.segersten.wreckage.game.domain.SpawnPoint;

public record BoardResponse(String mapId, String mapName, int width, int height, Set<Wall> walls, Set<Position> pits,
                            List<Checkpoint> checkpoints,List<SpawnPoint> spawnPoints,List<Conveyor> conveyors,
                            List<Rotator> rotators, Set<Position> controlPoints, Set<Position> obstacles) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.mapId(), board.mapName(), board.width(), board.height(), board.walls(),
                board.pits(), board.orderedCheckpoints(), board.spawnPoints(),board.conveyors(),board.rotators(),
                board.controlPoints(), board.obstacles());
    }
}
