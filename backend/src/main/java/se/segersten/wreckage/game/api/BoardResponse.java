package se.segersten.wreckage.game.api;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;
import se.segersten.wreckage.game.domain.Checkpoint;
import se.segersten.wreckage.game.domain.Conveyor;import se.segersten.wreckage.game.domain.Rotator;
import java.util.Set;
import java.util.List;
import se.segersten.wreckage.game.domain.SpawnPoint;

public record BoardResponse(int width, int height, Set<Wall> walls, Set<Position> pits,
                            Set<Checkpoint> checkpoints,List<SpawnPoint> spawnPoints,List<Conveyor> conveyors,List<Rotator> rotators) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.width(), board.height(), board.walls(), board.pits(), board.checkpoints(),
                board.spawnPoints(),board.conveyors(),board.rotators());
    }
}
