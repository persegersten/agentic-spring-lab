package se.segersten.wreckage.game.application;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Checkpoint;
import se.segersten.wreckage.game.domain.Conveyor;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Rotation;
import se.segersten.wreckage.game.domain.Rotator;
import se.segersten.wreckage.game.domain.Wall;
import se.segersten.wreckage.game.domain.MapDefinition;

@Component
@Profile("deterministic-e2e")
public class DeterministicTestGameBoardFactory implements GameBoardFactory {

    @Override
    public Board createBoard() {
        Board base = new Board(20, 20);
        return new Board(20, 20,
                Set.of(new Wall(new Position(0, 0), Direction.NORTH)),
                Set.of(new Position(4, 5)),
                Set.of(new Checkpoint("CP1", 1, new Position(2, 0)),
                        new Checkpoint("CP2", 2, new Position(4, 0)),
                        new Checkpoint("CP3", 3, new Position(4, 2)),
                        new Checkpoint("CP4", 4, new Position(2, 2))),
                base.spawnPoints(),
                List.of(new Conveyor(new Position(0, 2), Direction.EAST)),
                List.of(new Rotator(new Position(2, 2), Rotation.CLOCKWISE)),
                Set.of(new Position(5, 5)));
    }

    @Override public Board createBoard(MapDefinition map) {
        return createBoard().withDimensions(map.width(), map.height());
    }

    @Override public Board createBoard(MapDefinition map, int playerCount) {
        Board board = createBoard(map);
        return new Board(board.mapId(), board.mapName(), board.width(), board.height(), board.walls(), board.pits(),
                board.checkpoints(), board.spawnPoints().subList(0, playerCount), board.conveyors(), board.rotators(),
                board.controlPoints(), board.obstacles());
    }
}
