package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import se.segersten.wreckage.game.engine.BoardEffectEngine;
import se.segersten.wreckage.game.engine.MovementEngine;
import se.segersten.wreckage.game.support.SequentialFlagScenarios;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

class LaserFlagTheftTest {
    @Test
    void unshieldedHitTransfersBeforeDisplacementAndTurning() { assertHit(Scenario.THEFT); }

    @Test
    void shieldPreventsTheftDisplacementAndTurning() { assertHit(Scenario.THEFT_SHIELD); }

    @Test
    void blockedDisplacementDoesNotUndoTheftOrPreventTurning() { assertHit(Scenario.THEFT_BLOCKED); }

    @Test
    void pitCrashDoesNotUndoTheftAndCrashedTargetDoesNotTurn() { assertHit(Scenario.THEFT_PIT); }

    @ParameterizedTest
    @EnumSource(value = Scenario.class, names = {"THEFT_MIXED", "THEFT_CAPTURE_10", "THEFT_CAPTURE_11",
            "THEFT_CAPTURE_15", "THEFT_CAPTURE_16", "THEFT_CHAIN_15", "THEFT_CHAIN_16", "THEFT_CHAIN_20", "THEFT_CHAIN_21"})
    void mixedAgesAndExactCaptureAndResetBoundaries(Scenario scenario) {
        var original = new Game(UUID.randomUUID(), new Board(10, 10));
        original.addPlayer("Alice");
        boolean chain = scenario.name().startsWith("THEFT_CHAIN_");
        if (chain) original.addPlayer("Carol");
        original.addPlayer("Bob");
        Game game = SequentialFlagScenarios.configure(original, scenario);
        Player alice = named(game, "Alice");
        Player bob = named(game, "Bob");
        game.tick(game.getContinuous().nextTick);
        long tick = game.getContinuous().tick;
        var transfers = game.getRound().playback().stream().filter(e -> e.type() == RoundEventType.FLAG_TRANSFERRED).toList();
        if (scenario == Scenario.THEFT_MIXED) {
            assertThat(alice.getFlagAcquisitionTicks()).containsExactlyEntriesOf(Map.of("CP3", 9L));
            assertThat(bob.getFlagAcquisitionTicks()).containsExactlyEntriesOf(Map.of("CP1", 15L, "CP2", 15L));
            assertThat(transfers).extracting(RoundEvent::checkpointId).containsExactly("CP1", "CP2");
            assertThat(transfers.get(1).sequence()).isEqualTo(transfers.getFirst().sequence() + 1);
            assertThat(game.getRound().playback().stream().filter(e -> e.type() == RoundEventType.PUSH))
                    .allMatch(e -> e.sequence() > transfers.getLast().sequence());
        } else {
            boolean eligible = chain ? tick == 16 || tick == 20 : tick == 11 || tick == 15;
            Player owner = chain ? (eligible ? named(game, "Carol") : bob) : (eligible ? bob : alice);
            assertThat(owner.getFlagAcquisitionTicks()).containsExactlyEntriesOf(Map.of("CP1", eligible ? tick : chain ? 15L : 10L));
            assertThat(transfers).hasSize(chain && tick == 15 || eligible ? 1 : 0);
            if (chain && tick == 15) {
                var hits = game.getRound().playback().stream().filter(e -> e.type() == RoundEventType.WEAPON_HIT).toList();
                assertThat(hits).extracting(RoundEvent::sourcePlayerId).containsExactly(bob.getId(), named(game, "Carol").getId());
                assertThat(transfers.getFirst().sequence()).isBetween(hits.getFirst().sequence(), hits.getLast().sequence());
            }
        }
        Set<String> captured = scenario == Scenario.THEFT_MIXED ? Set.of("CP1", "CP2", "CP3") : Set.of("CP1");
        for (String flag : captured) {
            assertThat(game.getPlayers().stream().filter(p -> p.getVisitedCheckpoints().contains(flag))).hasSize(1);
        }
        for (Player player : game.getPlayers()) {
            assertThat(player.getVisitedCheckpoints()).isEqualTo(player.getFlagAcquisitionTicks().keySet());
            assertThat(player.getScore()).isZero();
        }
        assertThat(FlagProgress.nextAvailable(game.getBoard(), game.getPlayers()).id())
                .isEqualTo(scenario == Scenario.THEFT_MIXED ? "CP4" : "CP2");
        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
    }

    @ParameterizedTest
    @CsvSource({"16,16,false", "16,17,true", "16,21,true", "16,22,false",
            "20,20,false", "20,21,true", "20,25,true", "20,26,false"})
    void everySubsequentTheftResetsTheSameWindow(int secondTheft, int thirdHit, boolean eligible) {
        Player alice = Player.create(UUID.randomUUID(), "Alice", "a");
        Player bob = Player.create(UUID.randomUUID(), "Bob", "b");
        Player carol = Player.create(UUID.randomUUID(), "Carol", "c");
        var players = List.of(alice, bob, carol);
        alice.acquireFlag("CP1", 10);
        resolveHit(players, alice, bob, 15);
        resolveHit(players, bob, carol, secondTheft);
        assertThat(carol.getFlagAcquisitionTicks()).containsEntry("CP1", (long) secondTheft);
        resolveHit(players, carol, alice, thirdHit);
        assertThat((eligible ? alice : carol).getFlagAcquisitionTicks())
                .containsExactlyEntriesOf(Map.of("CP1", (long) (eligible ? thirdHit : secondTheft)));
        assertThat(players.stream().filter(p -> p.getVisitedCheckpoints().contains("CP1"))).hasSize(1);
    }

    private void resolveHit(List<Player> players, Player target, Player shooter, int tick) {
        Board board = new Board(10, 10, Set.of(), Set.of(), Set.of(new Checkpoint("CP1", 1, new Position(6, 6))));
        var vehicles = List.of(new VehicleState(new Vehicle(UUID.randomUUID(), target.getId()), new Position(2, 1), Direction.EAST),
                new VehicleState(new Vehicle(UUID.randomUUID(), shooter.getId()), new Position(0, 1), Direction.EAST));
        Round round = new Round(tick, Map.of(shooter.getId(), new PlayerProgram(shooter.getId(), 1, List.of(MovementOrder.LASER), true)),
                List.of(shooter.getId()), new GameState(board, vehicles));
        var engine = new MovementEngine();
        round.resolve(engine, new BoardEffectEngine(engine), players, GameConfiguration.defaults(), true);
    }

    private Player named(Game game, String name) {
        return game.getPlayers().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    private void assertHit(Scenario scenario) {
        var original = new Game(UUID.randomUUID(), new Board(10, 10));
        original.addPlayer("Alice"); original.addPlayer("Bob");
        Game game = SequentialFlagScenarios.configure(original, scenario);
        Player alice = game.getPlayers().getFirst();
        Player bob = game.getPlayers().get(1);
        boolean shield = scenario == Scenario.THEFT_SHIELD;
        Instant due = game.getContinuous().nextTick;
        game.tick(due);
        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getContinuous().tick).isEqualTo(11);
        assertThat(alice.getVisitedCheckpoints()).isEqualTo(shield ? Set.of("CP1") : Set.of());
        assertThat(bob.getVisitedCheckpoints()).isEqualTo(shield ? Set.of() : Set.of("CP1"));
        assertThat(shield ? alice.getFlagAcquisitionTicks() : bob.getFlagAcquisitionTicks())
                .containsEntry("CP1", shield ? 10L : 11L);
        assertThat(FlagProgress.nextAvailable(game.getBoard(), game.getPlayers()).id()).isEqualTo("CP2");
        assertThat(bob.getScore()).isZero(); // Theft is ownership transfer, not a new board capture.
        var events = game.getRound().playback();
        var transfers = events.stream().filter(event -> event.type() == RoundEventType.FLAG_TRANSFERRED).toList();
        assertThat(transfers).hasSize(shield ? 0 : 1);
        if (!shield) {
            RoundEvent transfer = transfers.getFirst();
            assertThat(transfer.playerId()).isEqualTo(alice.getId());
            assertThat(transfer.sourcePlayerId()).isEqualTo(bob.getId());
            assertThat(transfer.checkpointId()).isEqualTo("CP1");
            assertThat(transfer.actionType()).isEqualTo(ActionType.LASER);
            var hit = events.stream().filter(event -> event.type() == RoundEventType.WEAPON_HIT).findFirst().orElseThrow();
            assertThat(transfer.sequence()).isEqualTo(hit.sequence() + 1);
            assertThat(events.stream().filter(event -> Set.of(RoundEventType.PUSH, RoundEventType.PUSH_BLOCKED,
                    RoundEventType.CRASH, RoundEventType.TURN).contains(event.type())))
                    .allMatch(event -> event.sequence() > transfer.sequence());
        }
        var target = game.getVehicleStates().stream().filter(state -> state.vehicle().playerId().equals(alice.getId())).findFirst().orElseThrow();
        assertThat(target.position()).isEqualTo(new Position(shield || scenario == Scenario.THEFT_BLOCKED ? 2 : 3, 1));
        assertThat(target.status()).isEqualTo(scenario == Scenario.THEFT_PIT ? VehicleStatus.CRASHED : VehicleStatus.ACTIVE);
        assertThat(events.stream().filter(event -> event.type() == RoundEventType.TURN)).hasSize(shield || scenario == Scenario.THEFT_PIT ? 0 : 1);
        assertThat(alice.getScore()).isEqualTo(game.getConfiguration().checkpointScore()
                + (scenario == Scenario.THEFT_PIT ? game.getConfiguration().crashPenalty() : 0));
        long revision = game.getContinuous().revision;
        game.tick(due); // An early duplicate callback must not repeat the transfer.
        assertThat(game.getContinuous().revision).isEqualTo(revision);
    }

    @Test
    void aRealCellEntryRecordsItsContinuousCaptureTick() {
        Player player = Player.create(UUID.randomUUID(), "Alice", "a");
        Board board = new Board(6, 4, Set.of(), Set.of(), Set.of(new Checkpoint("CP1", 1, new Position(1, 1))));
        var vehicle = new VehicleState(new Vehicle(UUID.randomUUID(), player.getId()), new Position(0, 1), Direction.EAST);
        Round round = new Round(10, Map.of(player.getId(), new PlayerProgram(player.getId(), 1, List.of(MovementOrder.FORWARD_1), true)),
                List.of(player.getId()), new GameState(board, List.of(vehicle)));
        var engine = new MovementEngine();
        round.resolve(engine, new BoardEffectEngine(engine), List.of(player), GameConfiguration.defaults(), true);
        assertThat(player.getFlagAcquisitionTicks()).containsExactlyEntriesOf(Map.of("CP1", 10L));
    }
}
