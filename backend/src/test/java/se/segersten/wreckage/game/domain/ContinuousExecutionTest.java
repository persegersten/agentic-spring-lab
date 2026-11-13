package se.segersten.wreckage.game.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ContinuousExecutionTest {
    @Test void rejectsRetiredCommandsButReadsPreviouslyStoredQueues() {
        var queue = new CommandQueue();
        for (var command : List.of(MovementOrder.FORWARD_2, MovementOrder.FORWARD_3, MovementOrder.WAIT)) {
            assertThatThrownBy(() -> queue.append(command)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(queue.snapshot()).containsOnlyNulls();
        var restored = CommandQueue.decode("FORWARD_2,FORWARD_3,-,-,-");
        assertThat(restored.head()).isEqualTo(MovementOrder.FORWARD_2);
        restored.shift();
        assertThat(restored.head()).isEqualTo(MovementOrder.FORWARD_3);
    }
    @Test void appendOnlyUnlockedSlotsAndShift() {
        var q = new CommandQueue();
        assertThat(q.append(MovementOrder.FORWARD_1)).isEqualTo(4);
        assertThat(q.append(MovementOrder.TURN_LEFT)).isEqualTo(5);
        assertThatThrownBy(() -> q.append(MovementOrder.LASER)).isInstanceOf(IllegalStateException.class);
        for (int i=0;i<3;i++) { assertThat(q.head()).isEqualTo(MovementOrder.WAIT); q.shift(); }
        assertThat(q.head()).isEqualTo(MovementOrder.FORWARD_1);
        assertThatThrownBy(() -> q.snapshot().set(0, MovementOrder.LASER)).isInstanceOf(UnsupportedOperationException.class);
        q.shift(); assertThat(q.head()).isEqualTo(MovementOrder.TURN_LEFT);
        q.shift(); assertThat(q.head()).isEqualTo(MovementOrder.WAIT);
    }
    @Test void nopStillAppliesConveyorsAndSharedPushRulesWithoutControlPointScoring() {
        var spawns=List.of(new SpawnPoint(new Position(2,2),Direction.NORTH), new SpawnPoint(new Position(3,2),Direction.NORTH));
        var board=new Board(10,10,Set.of(),Set.of(),Set.of(),spawns,
                List.of(new Conveyor(new Position(2,2),Direction.EAST)),List.of(),Set.of(new Position(3,2)));
        var game=new Game(UUID.randomUUID(),board);
        game.addPlayer("A"); game.addPlayer("B"); game.enableContinuous(Instant.EPOCH,2000,600000);
        game.tick(Instant.EPOCH.plusSeconds(2));
        assertThat(game.getVehicleStates().get(0).position()).isEqualTo(new Position(3,2));
        assertThat(game.getVehicleStates().get(1).position()).isEqualTo(new Position(4,2));
        assertThat(game.getPlayers()).allMatch(p -> p.getScore()==0);
    }
    @Test void crashClearsQueueAndNextTickRespawns() {
        var game=new Game(UUID.randomUUID(),new Board(10,10));
        var player=game.addPlayer("A");game.addPlayer("B");game.enableContinuous(Instant.EPOCH,2000,600000);
        var queue=game.getContinuous().queues.get(player.getId());
        queue.append(MovementOrder.FORWARD_1);queue.append(MovementOrder.LASER);
        for(int i=1;i<=4;i++) game.tick(Instant.EPOCH.plusSeconds(i*2));
        assertThat(game.getVehicleStates().getFirst().status()).isEqualTo(VehicleStatus.CRASHED);
        assertThat(queue.snapshot()).containsOnlyNulls();
        game.tick(Instant.EPOCH.plusSeconds(10));
        assertThat(game.getVehicleStates().getFirst().status()).isEqualTo(VehicleStatus.ACTIVE);
    }
    @Test void receiptsSurviveConsumptionAndRoundTrip() {
        var c = new ContinuousExecution(Instant.EPOCH,2000,600000);
        var player = UUID.randomUUID(); var request = UUID.randomUUID();
        c.queues.put(player,new CommandQueue()); c.append(player,request,MovementOrder.LASER);
        c.queues.get(player).clear(); c = ContinuousExecution.decode(c.encode());
        assertThat(c.duplicate(player,request,MovementOrder.LASER)).isTrue();
        final var restored = c;
        assertThatThrownBy(() -> restored.duplicate(player,request,MovementOrder.TURN_LEFT)).isInstanceOf(IllegalStateException.class);
        assertThat(c.nextTick).isEqualTo(Instant.EPOCH.plusSeconds(2));
    }
    @Test void serverTickExecutesOnFourthTickAndNeverBursts() {
        var game = new Game(UUID.randomUUID(), new Board(10,10));
        var a=game.addPlayer("A"); game.addPlayer("B");
        game.enableContinuous(Instant.EPOCH,2000,600000);
        game.getContinuous().queues.get(a.getId()).append(MovementOrder.TURN_RIGHT);
        var initial=game.getVehicleStates().getFirst().orientation();
        game.tick(Instant.EPOCH.plusMillis(1999)); assertThat(game.getContinuous().tick).isZero();
        for(int i=1;i<=3;i++) { game.tick(Instant.EPOCH.plusSeconds(i*2)); assertThat(game.getVehicleStates().getFirst().orientation()).isEqualTo(initial); }
        game.tick(Instant.EPOCH.plusSeconds(8));
        assertThat(game.getVehicleStates().getFirst().orientation()).isNotEqualTo(initial);
        game.tick(Instant.EPOCH.plusSeconds(100)); assertThat(game.getContinuous().tick).isEqualTo(5);
        game.tick(Instant.EPOCH.plusSeconds(100)); assertThat(game.getContinuous().tick).isEqualTo(5);
        game.tick(Instant.EPOCH.plusSeconds(600)); assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getContinuous().tick).isEqualTo(5);
    }
}
