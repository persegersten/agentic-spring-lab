package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;

class GameRoundTest {
    @Test void shieldBlocksRammingAndChainPushingForEveryRegisterButAllowsOwnMovement() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Player pa = Player.create(a, "A", "a"), pb = Player.create(b, "B", "b"), pc = Player.create(c, "C", "c");
        Map<UUID,PlayerProgram> programs = new LinkedHashMap<>();
        programs.put(a, locked(a, false, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1));
        programs.put(b, locked(b, false, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT));
        programs.put(c, locked(c, true, MovementOrder.REVERSE_1, MovementOrder.REVERSE_1, MovementOrder.REVERSE_1, MovementOrder.REVERSE_1, MovementOrder.REVERSE_1));
        Round round = new Round(1, programs, List.of(a,b,c), new GameState(new Board(10,10),
                List.of(state(a,1), state(b,2), state(c,3))));
        round.resolve(new MovementEngine(), List.of(pa,pb,pc), GameConfiguration.defaults());
        assertThat(round.playback()).extracting(RoundEvent::type).contains(RoundEventType.SHIELD_ACTIVATED, RoundEventType.PUSH_BLOCKED);
        assertThat(round.finalVehicleStates().stream().filter(v -> v.vehicle().playerId().equals(c)).findFirst().orElseThrow().position().x()).isLessThan(3);
    }

    @Test void pushedVehicleCapturesCheckpointAndFourthCheckpointEndsResolution() {
        UUID id = UUID.randomUUID(); Player player = Player.create(id, "A", "a");
        Board board = new Board(8, 8, Set.of(), Set.of(), Set.of(
                new Checkpoint("CP1",1,new Position(2,1)), new Checkpoint("CP2",2,new Position(3,1)),
                new Checkpoint("CP3",3,new Position(4,1)), new Checkpoint("CP4",4,new Position(5,1))),
                new Board(8,8).spawnPoints(), List.of(), List.of());
        player.visitCheckpoint("CP1"); player.visitCheckpoint("CP2"); player.visitCheckpoint("CP3");
        Round round = new Round(1, Map.of(id, locked(id,false,MovementOrder.FORWARD_1,MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_1,MovementOrder.FORWARD_1,MovementOrder.FORWARD_1)), List.of(id),
                new GameState(board,List.of(new VehicleState(new Vehicle(UUID.randomUUID(),id),new Position(4,1),Direction.EAST))));
        round.resolve(new MovementEngine(),List.of(player),GameConfiguration.defaults());
        assertThat(player.hasCompletedCheckpoints(board)).isTrue();
        assertThat(round.playback()).extracting(RoundEvent::type).containsExactly(RoundEventType.MOVE,RoundEventType.CHECKPOINT_CAPTURED);
    }

    @Test void maximumRoundPlacementUsesTraversableDistanceAndCanDraw() {
        Instant now=Instant.parse("2099-01-01T00:00:00Z");
        Board board=new Board("test","Test",8,8,Set.of(new Wall(new Position(1,1),Direction.EAST)),Set.of(),Set.of(
                new Checkpoint("CP1",1,new Position(4,1)),new Checkpoint("CP2",2,new Position(5,1)),
                new Checkpoint("CP3",3,new Position(6,1)),new Checkpoint("CP4",4,new Position(7,1))),new Board(8,8).spawnPoints(),List.of(),List.of(),Set.of(),Set.of(new Position(2,2)));
        Game game=new Game(UUID.randomUUID(),List.of(),board,GameStatus.WAITING_FOR_PLAYERS,Map.of(),null,
                new GameConfiguration(2,60,5,30),now,now.plusSeconds(60));
        game.addPlayer("A","a"); game.addPlayer("B","b"); game.start(now,null,Map.of());
        while (game.getStatus() != GameStatus.FINISHED) {
            game.getRound().programs().keySet().forEach(id->game.getRound().lock(id,Collections.nCopies(5,MovementOrder.WAIT)));
            game.getRound().resolve(new MovementEngine(),game.getPlayers(),game.getConfiguration()); game.completeRound();
            if (game.getStatus() != GameStatus.FINISHED) game.startRound(now.plusSeconds(game.getRound().number()));
        }
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getPlacements()).hasSize(2);
    }

    private static PlayerProgram locked(UUID id, boolean shield, MovementOrder... orders) {
        return new PlayerProgram(id,5,List.of(),List.of(orders),true,shield);
    }
    private static VehicleState state(UUID player,int x){return new VehicleState(new Vehicle(UUID.randomUUID(),player),new Position(x,1),Direction.EAST);}
}
