package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import se.segersten.wreckage.TestcontainersConfiguration;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.VehicleState;

@Import(TestcontainersConfiguration.class)
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
    void twoPlayersCanSubmitTheLastRequiredProgramsConcurrently() throws Exception {
        Fixture fixture = createStartedGame("Alice", "Bob");
        List<MovementOrder> aliceProgram = programFor(fixture.gameId(), fixture.alice().player().getId());
        List<MovementOrder> bobProgram = programFor(fixture.gameId(), fixture.bob().player().getId());

        List<Outcome> outcomes = runConcurrently(
                () -> gameService.submitProgram(fixture.gameId(), fixture.alice().player().getId(),
                        fixture.alice().token(), aliceProgram),
                () -> gameService.submitProgram(fixture.gameId(), fixture.bob().player().getId(),
                        fixture.bob().token(), bobProgram));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        Game persisted = reload(fixture.gameId());
        assertThat(persisted.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(persisted.getRound().programs().get(fixture.alice().player().getId()).commands())
                .containsExactlyElementsOf(aliceProgram);
        assertThat(persisted.getRound().programs().get(fixture.bob().player().getId()).commands())
                .containsExactlyElementsOf(bobProgram);
        assertSingleResolution(persisted);
    }

    @Test
    void schedulerTimeoutCanCompeteWithAHumanSubmission() throws Exception {
        Fixture fixture = createStartedGame("Scheduler Alice", "Scheduler Bob");
        List<MovementOrder> aliceProgram = programFor(fixture.gameId(), fixture.alice().player().getId());
        gameService.saveProgramDraft(fixture.gameId(), fixture.alice().player().getId(),
                fixture.alice().token(), aliceProgram);
        expireCurrentRound(fixture.gameId());

        List<Outcome> outcomes = runConcurrently(
                () -> {
                    gameService.completeExpiredPlanning();
                    return null;
                },
                () -> gameService.submitProgram(fixture.gameId(), fixture.alice().player().getId(),
                        fixture.alice().token(), aliceProgram));

        assertThat(outcomes.get(0)).matches(Outcome::succeeded);
        assertThat(outcomes.get(1).error()).isIn(null, IllegalStateException.class);
        Game persisted = reload(fixture.gameId());
        assertThat(persisted.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(persisted.getRound().programs().get(fixture.alice().player().getId()).commands())
                .containsExactlyElementsOf(aliceProgram);
        assertThat(persisted.getRound().programs().values()).allMatch(PlayerProgram::ready);
        assertSingleResolution(persisted);
    }

    @Test
    void twoClientsStartingTheNextRoundCreateOnlyOneRound() throws Exception {
        Fixture fixture = createResolvedGame("Round Alice", "Round Bob");

        List<Outcome> outcomes = runConcurrently(
                () -> gameService.startRound(fixture.gameId(), fixture.alice().player().getId(),
                        fixture.alice().token(), 1),
                () -> gameService.startRound(fixture.gameId(), fixture.bob().player().getId(),
                        fixture.bob().token(), 1));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes).extracting(Outcome::roundNumber).containsOnly(2);
        Game persisted = reload(fixture.gameId());
        assertThat(persisted.getRound().number()).isEqualTo(2);
        assertThat(persisted.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(persisted.getRound().programs()).containsOnlyKeys(
                fixture.alice().player().getId(), fixture.bob().player().getId());
        assertThat(persisted.getRound().initiative()).containsExactlyInAnyOrder(
                fixture.alice().player().getId(), fixture.bob().player().getId());
    }

    @Test
    void repeatedRequestsCannotCompleteTheSameRoundOrBypassValidation() throws Exception {
        Fixture fixture = createStartedGame("Repeat Alice", "Repeat Bob");
        List<MovementOrder> aliceProgram = programFor(fixture.gameId(), fixture.alice().player().getId());
        List<MovementOrder> bobProgram = programFor(fixture.gameId(), fixture.bob().player().getId());
        gameService.submitProgram(fixture.gameId(), fixture.alice().player().getId(),
                fixture.alice().token(), aliceProgram);

        List<Outcome> outcomes = runConcurrently(
                () -> gameService.submitProgram(fixture.gameId(), fixture.bob().player().getId(),
                        fixture.bob().token(), bobProgram),
                () -> gameService.submitProgram(fixture.gameId(), fixture.bob().player().getId(),
                        fixture.bob().token(), bobProgram));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement()
                .extracting(Outcome::error).isEqualTo(IllegalStateException.class);
        Game resolved = reload(fixture.gameId());
        assertThat(resolved.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertSingleResolution(resolved);

        Fixture validationFixture = createStartedGame("Validation Alice", "Validation Bob");
        List<MovementOrder> validProgram = programFor(
                validationFixture.gameId(), validationFixture.alice().player().getId());
        StateFingerprint beforeInvalidRequests = fingerprint(reload(validationFixture.gameId()));

        List<Outcome> invalidOutcomes = runConcurrently(
                () -> gameService.submitProgram(validationFixture.gameId(),
                        validationFixture.alice().player().getId(), "invalid-token", validProgram),
                () -> gameService.submitProgram(validationFixture.gameId(),
                        validationFixture.alice().player().getId(), validationFixture.alice().token(),
                        List.of(MovementOrder.WAIT, MovementOrder.WAIT,
                                MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT)));

        assertThat(invalidOutcomes).extracting(Outcome::error)
                .containsExactlyInAnyOrder(SecurityException.class, IllegalArgumentException.class);
        assertThat(fingerprint(reload(validationFixture.gameId()))).isEqualTo(beforeInvalidRequests);
    }

    @Test
    void concurrentRequestsForDifferentGamesRemainIsolated() throws Exception {
        Fixture first = createStartedGame("First Alice", "First Bob");
        Fixture second = createStartedGame("Second Alice", "Second Bob");
        List<MovementOrder> firstAliceProgram = programFor(first.gameId(), first.alice().player().getId());
        List<MovementOrder> firstBobProgram = programFor(first.gameId(), first.bob().player().getId());
        List<MovementOrder> secondAliceProgram = programFor(second.gameId(), second.alice().player().getId());
        List<MovementOrder> secondBobProgram = programFor(second.gameId(), second.bob().player().getId());
        gameService.submitProgram(first.gameId(), first.alice().player().getId(),
                first.alice().token(), firstAliceProgram);
        gameService.submitProgram(second.gameId(), second.alice().player().getId(),
                second.alice().token(), secondAliceProgram);

        List<Outcome> outcomes = runConcurrently(
                () -> gameService.submitProgram(first.gameId(), first.bob().player().getId(),
                        first.bob().token(), firstBobProgram),
                () -> gameService.submitProgram(second.gameId(), second.bob().player().getId(),
                        second.bob().token(), secondBobProgram));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        Game persistedFirst = reload(first.gameId());
        Game persistedSecond = reload(second.gameId());
        assertThat(persistedFirst.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(persistedSecond.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(persistedFirst.getPlayers()).extracting(player -> player.getName())
                .containsExactly("First Alice", "First Bob");
        assertThat(persistedSecond.getPlayers()).extracting(player -> player.getName())
                .containsExactly("Second Alice", "Second Bob");
        assertThat(persistedFirst.getRound().programs().get(first.bob().player().getId()).commands())
                .containsExactlyElementsOf(firstBobProgram);
        assertThat(persistedSecond.getRound().programs().get(second.bob().player().getId()).commands())
                .containsExactlyElementsOf(secondBobProgram);
        assertThat(persistedFirst.getRound().programs()).doesNotContainKeys(
                second.alice().player().getId(), second.bob().player().getId());
        assertThat(persistedSecond.getRound().programs()).doesNotContainKeys(
                first.alice().player().getId(), first.bob().player().getId());
        assertSingleResolution(persistedFirst);
        assertSingleResolution(persistedSecond);
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
