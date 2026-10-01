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

@Component
@Profile("!deterministic-e2e")
public class DefaultGameBoardFactory implements GameBoardFactory {

    @Override
    public Board createBoard() {
        Board base = new Board(20, 20, Set.of(
                new Wall(new Position(0, 0), Direction.NORTH)),
                Set.of(new Position(4, 5)),
                Set.of(new Checkpoint("checkpoint-1", new Position(3, 3))));
        return new Board(base.width(), base.height(), base.walls(), base.pits(), base.checkpoints(),
                base.spawnPoints(), List.of(new Conveyor(new Position(0, 0), Direction.EAST)),
                List.of(new Rotator(new Position(2, 0), Rotation.CLOCKWISE)),
                Set.of(new Position(5, 5)));
    }
}
