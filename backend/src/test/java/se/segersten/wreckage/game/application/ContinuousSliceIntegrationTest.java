package se.segersten.wreckage.game.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.segersten.wreckage.game.domain.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"wreckage.scheduling.enabled=false", "spring.datasource.url=jdbc:h2:mem:continuous-slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class ContinuousSliceIntegrationTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired GameService service;
    @Autowired GameRepository repository;
    @Autowired PlatformTransactionManager transactions;

    @Test void shieldRollbackAndConcurrentRequestsConsumeExactlyOneUse() throws Exception {
        var hosted = service.createHostedGame();
        var id = hosted.game().getId();
        var host = service.addPlayer(id, "Host", hosted.hostToken());
        service.addPlayer(id, "Other");
        service.startGame(id, hosted.hostToken());
        var rolledBack = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.activateShield(id, host.player().getId(), host.token(), rolledBack);
            tx.setRollbackOnly();
        });
        assertThat(service.getGame(id).isShieldAvailable(host.player().getId())).isTrue();
        assertThat(service.getGame(id).getContinuous().shieldPending(host.player().getId())).isFalse();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) tasks.add(executor.submit(() -> {
                start.await();
                try { service.activateShield(id, host.player().getId(), host.token(), UUID.randomUUID()); return true; }
                catch (IllegalStateException expected) { return false; }
            }));
            start.countDown();
            int accepted = 0;
            for (var task : tasks) if (task.get(10, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        var restored = service.getGame(id);
        assertThat(restored.isShieldAvailable(host.player().getId())).isFalse();
        assertThat(restored.getContinuous().shieldTicksRemaining(host.player().getId())).isEqualTo(5);
    }

    @Test void laserTickPersistsSpinAndRollbackRetryUsesTheSameRandomResult() {
        var now = Instant.now();
        var board = new Board(7, 7, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.List.of(
                new SpawnPoint(new Position(1, 3), Direction.EAST), new SpawnPoint(new Position(3, 3), Direction.NORTH)));
        var game = new Game(UUID.randomUUID(), board);
        var shooter = game.addPlayer("Shooter");
        var target = game.addPlayer("Target");
        game.enableContinuous(now, 2000, 600000);
        var queue = game.getContinuous().queues.get(shooter.getId());
        queue.append(MovementOrder.LASER);
        for (int i = 0; i < 2; i++) queue.shift();
        game.getContinuous().nextTick = now.minusSeconds(1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> repository.save(game));
        var first = new java.util.concurrent.atomic.AtomicReference<java.util.List<RoundEvent>>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.advanceTick(game.getId());
            first.set(service.getGame(game.getId()).getRound().playback());
            tx.setRollbackOnly();
        });
        assertThat(service.getGame(game.getId()).getContinuous().tick).isZero();
        service.advanceTick(game.getId());
        var restored = service.getGame(game.getId());
        assertThat(restored.getRound().playback()).isEqualTo(first.get());
        var turn = restored.getRound().playback().stream().filter(e -> e.type() == RoundEventType.TURN).findFirst().orElseThrow();
        assertThat(turn.actionType()).isEqualTo(ActionType.LASER);
        assertThat(turn.sourcePlayerId()).isEqualTo(shooter.getId());
        assertThat(restored.getVehicleStates()).filteredOn(v -> v.vehicle().playerId().equals(target.getId()))
                .singleElement().satisfies(v -> {
                    assertThat(v.position()).isEqualTo(new Position(4, 3));
                    assertThat(v.orientation()).isEqualTo(turn.newDirection());
                });
    }

    @Test void rolledBackTickRestoresBotQueuesAndRevision() {
        var hosted = service.createHostedGame();
        var id = hosted.game().getId();
        var host = service.addPlayer(id, "Host", hosted.hostToken());
        var bot = service.addBot(id, host.player().getId(), host.token());
        var started = service.startGame(id, hosted.hostToken());
        var initialQueue = started.getContinuous().queues.get(bot.getId()).snapshot();
        long revision = started.getContinuous().revision;
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var game = repository.findByIdForUpdate(id).orElseThrow();
            game.getContinuous().nextTick = Instant.EPOCH;
            repository.save(game);
            service.advanceTick(id);
            assertThat(service.getGame(id).getContinuous().tick).isEqualTo(1);
            tx.setRollbackOnly();
        });
        var restored = service.getGame(id);
        assertThat(restored.getContinuous().tick).isZero();
        assertThat(restored.getContinuous().revision).isEqualTo(revision);
        assertThat(restored.getContinuous().queues.get(bot.getId()).snapshot()).isEqualTo(initialQueue);
    }

    @Test void botQueuesPersistAndRefillThroughBothTickPaths() {
        var hosted = service.createHostedGame();
        var id = hosted.game().getId();
        var human = service.addPlayer(id, "Host", hosted.hostToken());
        var bot = service.addBot(id, human.player().getId(), human.token());
        service.startGame(id, hosted.hostToken());
        var initial = service.getGame(id).getContinuous().queues.get(bot.getId()).snapshot();
        assertThat(initial.subList(0, 2)).containsOnlyNulls();
        assertThat(initial.subList(2, 5)).doesNotContainNull();
        long revision = service.getGame(id).getContinuous().revision;
        service.getPlayerGame(id, human.player().getId(), human.token());
        assertThat(service.getGame(id).getContinuous().revision).isEqualTo(revision);

        for (int tick = 1; tick <= 2; tick++) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var game = repository.findByIdForUpdate(id).orElseThrow();
                game.getContinuous().nextTick = Instant.EPOCH;
                repository.save(game);
            });
            if (tick == 1) service.advanceTick(id);
            else service.appendCommand(id, human.player().getId(), human.token(), MovementOrder.TURN_LEFT, UUID.randomUUID());
            var game = service.getGame(id);
            assertThat(game.getContinuous().tick).isEqualTo(tick);
            var queue = game.getContinuous().queues.get(bot.getId()).snapshot();
            assertThat(queue.get(2 - tick)).isEqualTo(initial.get(2));
            assertThat(queue.subList(2, 5)).allMatch(command -> command != null && command.isProgrammingCard());
            long filledRevision = game.getContinuous().revision;
            service.advanceTick(id);
            assertThat(service.getGame(id).getContinuous().revision).isEqualTo(filledRevision);
        }
    }

    @Test void twoHumansSharePersistedTicksAndImmutableQueues() throws Exception {
        var hosted = service.createHostedGame(); var id = hosted.game().getId();
        var a = service.addPlayer(id,"A",hosted.hostToken()); var b = service.addPlayer(id,"B");
        service.startGame(id,hosted.hostToken());
        var request = UUID.randomUUID();
        var http=java.net.http.HttpClient.newHttpClient();
        var accepted=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/games/"+id+"/commands"))
                .header("X-Player-Id",a.player().getId().toString()).header("X-Player-Token",a.token()).header("Content-Type","application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"command\":\"TURN_RIGHT\",\"requestId\":\""+request+"\"}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(json.readTree(accepted.body()).path("continuous").path("queue").get(2).asText()).isEqualTo("TURN_RIGHT");
        var other=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/games/"+id+"/players/"+b.player().getId()))
                .header("X-Player-Token",b.token()).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(other.statusCode()).isEqualTo(200);
        assertThat(json.readTree(other.body()).path("continuous").path("queue").get(2).isNull()).isTrue();
        service.appendCommand(id,b.player().getId(),b.token(),MovementOrder.TURN_LEFT,UUID.randomUUID());
        service.appendCommand(id,a.player().getId(),a.token(),MovementOrder.FORWARD_1,UUID.randomUUID());
        service.appendCommand(id,a.player().getId(),a.token(),MovementOrder.REVERSE_1,UUID.randomUUID());
        assertThatThrownBy(() -> service.appendCommand(id,a.player().getId(),a.token(),MovementOrder.LASER,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.saveProgramDraft(id,a.player().getId(),a.token(),java.util.List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.appendCommand(id,a.player().getId(),"wrong",MovementOrder.LASER,UUID.randomUUID())).isInstanceOf(SecurityException.class);
        for(int tick=1;tick<=4;tick++) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var g=repository.findByIdForUpdate(id).orElseThrow();
                g.getContinuous().nextTick=Instant.EPOCH; repository.save(g);
            });
            service.advanceTick(id);
        }
        var av=service.getPlayerGame(id,a.player().getId(),a.token());
        var bv=service.getPlayerGame(id,b.player().getId(),b.token());
        assertThat(av.getContinuous().tick).isEqualTo(4);
        assertThat(av.getVehicleStates()).usingRecursiveComparison().isEqualTo(bv.getVehicleStates());
        long revision=av.getContinuous().revision;
        var retry=service.appendCommand(id,a.player().getId(),a.token(),MovementOrder.TURN_RIGHT,request);
        assertThat(retry.getContinuous().revision).isEqualTo(revision);
        assertThat(retry.getContinuous().queues.get(a.player().getId()).snapshot()).doesNotContain(MovementOrder.TURN_RIGHT);
    }
}
