package se.segersten.wreckage.game.domain;
import static org.assertj.core.api.Assertions.*;import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;import se.segersten.wreckage.game.engine.MovementEngine;
class GameRoundTest{
 @Test void startsEmptyProgramsAndResolvesCompleteOnes(){Instant now=Instant.parse("2099-01-01T00:00:00Z");Game game=new Game(UUID.randomUUID(),List.of(),new Board(8,8),GameStatus.RUNNING,Map.of(),null,new GameConfiguration(6,60,3,30),now,now.plusSeconds(60));Player player=game.addPlayer("Alice","a");Round round=game.startRound(now);assertThat(round.programs().get(player.getId()).commands()).isEmpty();round.lock(player.getId(),List.of(MovementOrder.FORWARD_1,MovementOrder.TURN_RIGHT,MovementOrder.WAIT));round.resolve(new MovementEngine());assertThat(round.phase()).isEqualTo(RoundPhase.PLAYBACK);}
 @Test void assignsDistinctInitialVehiclePositions(){Game game=new Game(UUID.randomUUID(),new Board(5,5));game.addPlayer("Alice","a");game.addPlayer("Bob","b");assertThat(game.getVehicleStates()).extracting(VehicleState::position).doesNotHaveDuplicates();}
}
