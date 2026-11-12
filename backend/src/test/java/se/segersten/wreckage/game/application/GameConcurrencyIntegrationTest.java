package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.VehicleState;

@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:concurrency-test;MODE=PostgreSQL;DB_CLOSE_DELAY=0;DATABASE_TO_LOWER=TRUE")
@SpringBootTest(properties = "wreckage.scheduling.enabled=false")
class GameConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoPlayersCanAppendConcurrently() throws Exception {
        Fixture f = createStartedGame("Alice", "Bob");
        var request = UUID.randomUUID();
        var outcomes=runConcurrently(
            () -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),f.alice().token(),MovementOrder.TURN_LEFT,request),
            () -> gameService.appendCommand(f.gameId(),f.bob().player().getId(),f.bob().token(),MovementOrder.TURN_RIGHT,request));
        assertThat(outcomes).allMatch(Outcome::succeeded);
        var c=reload(f.gameId()).getContinuous();
        assertThat(c.queues.get(f.alice().player().getId()).snapshot().get(3)).isEqualTo(MovementOrder.TURN_LEFT);
        assertThat(c.queues.get(f.bob().player().getId()).snapshot().get(3)).isEqualTo(MovementOrder.TURN_RIGHT);
    }

    @Test
    void tickCanCompeteWithAnAppend() throws Exception {
        Fixture f = createStartedGame("Alice", "Bob");
        var request = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var game=gameRepository.findByIdForUpdate(f.gameId()).orElseThrow();
            game.getContinuous().nextTick=Instant.EPOCH;gameRepository.save(game);
        });
        var outcomes=runConcurrently(
            () -> {gameService.advanceTick(f.gameId());return null;},
            () -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),f.alice().token(),MovementOrder.TURN_LEFT,request));
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(reload(f.gameId()).getContinuous().tick).isEqualTo(1);
        assertThat(reload(f.gameId()).getContinuous().queues.get(f.alice().player().getId()).snapshot().get(3)).isEqualTo(MovementOrder.TURN_LEFT);
    }

    @Test
    void concurrentSchedulerCallsExecuteOneTick() throws Exception {
        Fixture f = createStartedGame("Alice", "Bob");
        var request = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var game=gameRepository.findByIdForUpdate(f.gameId()).orElseThrow();
            game.getContinuous().nextTick=Instant.EPOCH;gameRepository.save(game);
        });
        var outcomes=runConcurrently(
            () -> {gameService.advanceTick(f.gameId());return null;},
            () -> {gameService.advanceTick(f.gameId());return null;});
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(reload(f.gameId()).getContinuous().tick).isEqualTo(1);
    }

    @Test
    void duplicateAppendsCannotBypassAuthenticationOrInputValidation() throws Exception {
        Fixture f = createStartedGame("Alice", "Bob");
        var request = UUID.randomUUID();
        var outcomes=runConcurrently(
            () -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),f.alice().token(),MovementOrder.TURN_LEFT,request),
            () -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),f.alice().token(),MovementOrder.TURN_LEFT,request));
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(reload(f.gameId()).getContinuous().revision).isEqualTo(2);
        assertThat(reload(f.gameId()).getContinuous().queues.get(f.alice().player().getId()).snapshot().get(4)).isNull();
        assertThatThrownBy(() -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),"wrong",MovementOrder.TURN_LEFT,request)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),f.alice().token(),MovementOrder.TURN_RIGHT,request)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentRequestsForDifferentGamesRemainIsolated() throws Exception {
        Fixture f = createStartedGame("Alice", "Bob");
        var request = UUID.randomUUID();
        Fixture other=createStartedGame("Other A","Other B");
        var outcomes=runConcurrently(
            () -> gameService.appendCommand(f.gameId(),f.alice().player().getId(),f.alice().token(),MovementOrder.LASER,request),
            () -> gameService.appendCommand(other.gameId(),other.bob().player().getId(),other.bob().token(),MovementOrder.TURN_RIGHT,request));
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(reload(f.gameId()).getContinuous().queues).doesNotContainKey(other.bob().player().getId());
        assertThat(reload(other.gameId()).getContinuous().queues).doesNotContainKey(f.alice().player().getId());
    }

    @Test
    void concurrentHumanJoinAndBotCreationShareTheSameCapacityLock() throws Exception {
        GameService.HostedGame hosted = gameService.createHostedGame(new GameConfiguration(3, 60, 5, 120));
        GameService.PlayerJoin host = gameService.addPlayer(
                hosted.game().getId(), "Capacity Host", hosted.hostToken());

        List<Outcome> outcomes = runConcurrently(
                () -> gameService.addPlayer(hosted.game().getId(), "Capacity Guest"),
                () -> gameService.addBot(hosted.game().getId(), host.player().getId(), host.token()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        Game persisted = reload(hosted.game().getId());
        assertThat(persisted.getPlayers()).hasSize(3);
        assertThat(persisted.getPlayers()).filteredOn(player -> player.isAutomated()).hasSize(1);
        assertThat(persisted.getHostPlayerId()).isEqualTo(host.player().getId());
    }

    private Fixture createStartedGame(String aliceName, String bobName) {
        GameService.HostedGame hosted = gameService.createHostedGame(new GameConfiguration(2, 60, 5, 120));
        GameService.PlayerJoin alice = gameService.addPlayer(hosted.game().getId(), aliceName, hosted.hostToken());
        GameService.PlayerJoin bob = gameService.addPlayer(hosted.game().getId(), bobName);
        gameService.startGame(hosted.game().getId(), hosted.hostToken());
        return new Fixture(hosted.game().getId(), alice, bob);
    }

    private Fixture createResolvedGame(String aliceName, String bobName) {
        Fixture fixture = createStartedGame(aliceName, bobName);
        gameService.submitProgram(fixture.gameId(), fixture.alice().player().getId(), fixture.alice().token(),
                programFor(fixture.gameId(), fixture.alice().player().getId()));
        gameService.submitProgram(fixture.gameId(), fixture.bob().player().getId(), fixture.bob().token(),
                programFor(fixture.gameId(), fixture.bob().player().getId()));
        return fixture;
    }

    private List<MovementOrder> programFor(UUID gameId, UUID playerId) {
        return reload(gameId).getRound().programs().get(playerId).hand().subList(0, 5);
    }

    private Game reload(UUID gameId) {
        return new TransactionTemplate(transactionManager).execute(status -> gameRepository.findById(gameId).orElseThrow());
    }

    private void expireCurrentRound(UUID gameId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Game current = gameRepository.findByIdForUpdate(gameId).orElseThrow();
            Round round = current.getRound();
            Round expired = new Round(round.number(), round.phase(), round.programs(), round.initiative(),
                    round.initialState(), round.playback(), Instant.EPOCH, round.initialScores(), round.startEvents());
            Map<UUID, VehicleState> vehicles = new LinkedHashMap<>();
            current.getVehicleStates().forEach(state -> vehicles.put(state.vehicle().playerId(), state));
            gameRepository.save(new Game(current.getId(), current.getPlayers(), current.getBoard(), current.getStatus(),
                    vehicles, expired, current.getConfiguration(), current.getCreatedAt(), current.getJoinDeadline(),
                    current.getHostTokenHash(), current.getHostPlayerId()));
        });
    }

    @SafeVarargs
    private final List<Outcome> runConcurrently(ThrowingSupplier<?>... requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.length);
        CountDownLatch ready = new CountDownLatch(requests.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (ThrowingSupplier<?> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        return new Outcome(null, java.util.concurrent.TimeoutException.class);
                    }
                    try {
                        Object result = request.get();
                        Integer roundNumber = result instanceof Round round ? round.number() : null;
                        return new Outcome(roundNumber, null);
                    } catch (Throwable error) {
                        return new Outcome(null, error.getClass());
                    }
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return List.copyOf(outcomes);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertSingleResolution(Game game) {
        assertThat(game.getRound().playback()).extracting(event -> event.sequence()).doesNotHaveDuplicates();
        assertThat(game.getRound().playback()).filteredOn(event -> event.type() == RoundEventType.SCORE_CHANGED
                        && event.checkpointId() != null)
                .extracting(event -> event.playerId() + ":" + event.checkpointId()).doesNotHaveDuplicates();
        game.getPlayers().forEach(player -> assertThat(player.getVisitedCheckpoints()).doesNotHaveDuplicates());
    }

    private StateFingerprint fingerprint(Game game) {
        return new StateFingerprint(game.getRound().number(), game.getRound().phase(), game.getRound().programs(),
                game.getRound().playback(), game.getPlayers().stream().collect(java.util.stream.Collectors.toMap(
                        player -> player.getId(), player -> List.of(player.getScore(), player.getVisitedCheckpoints()))));
    }

    private record Fixture(UUID gameId, GameService.PlayerJoin alice, GameService.PlayerJoin bob) {}

    private record Outcome(Integer roundNumber, Class<? extends Throwable> error) {
        boolean succeeded() {
            return error == null;
        }
    }

    private record StateFingerprint(int roundNumber, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                                    List<?> playback, Map<UUID, List<?>> playerProgress) {}

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
