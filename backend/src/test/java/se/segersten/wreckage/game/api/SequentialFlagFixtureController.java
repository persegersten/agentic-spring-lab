package se.segersten.wreckage.game.api;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.support.SequentialFlagScenarios;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

/** Available only on the test classpath with an explicitly enabled local fixture profile. */
@RestController
@Profile("sequential-flags-e2e")
@RequestMapping("/games/test-fixtures/flags")
@Transactional
public class SequentialFlagFixtureController {
    private final GameService service;
    private final GameRepository repository;
    private final TransactionTemplate transaction;

    public SequentialFlagFixtureController(GameService service, GameRepository repository, PlatformTransactionManager manager) {
        this.service = service;
        this.repository = repository;
        this.transaction = new TransactionTemplate(manager);
    }

    @GetMapping
    public List<Scenario> scenarios() {
        return List.of(Scenario.values());
    }

    @PostMapping
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Fixture create(@RequestParam Scenario scenario) {
        var hosted = service.createHostedGame(GameConfiguration.defaults());
        UUID id = hosted.game().getId();
        var alice = service.addPlayer(id, "Alice", hosted.hostToken());
        var carol = scenario.name().startsWith("THEFT_CHAIN_") ? service.addPlayer(id, "Carol") : null;
        var bob = service.addPlayer(id, "Bob");
        // Commit ordinary creation/join requests before loading database-generated timestamps.
        transaction.executeWithoutResult(status -> {
            var original = repository.findByIdForUpdate(id).orElseThrow();
            repository.save(SequentialFlagScenarios.configure(original, scenario));
        });
        var sessions = new java.util.ArrayList<Session>();
        sessions.add(new Session(id, alice.player().getId(), alice.token()));
        sessions.add(new Session(id, bob.player().getId(), bob.token()));
        if (carol != null) sessions.add(new Session(id, carol.player().getId(), carol.token()));
        return new Fixture(id, List.copyOf(sessions));
    }

    @PostMapping("/{gameId}/ticks")
    public GameResponse tick(@PathVariable UUID gameId, @RequestParam(defaultValue = "1") int count) {
        if (count < 1 || count > 10) throw new IllegalArgumentException("Tick count must be between 1 and 10");
        var game = repository.findByIdForUpdate(gameId).orElseThrow();
        if (game.getContinuous() == null || game.getContinuous().nextTick.getEpochSecond() < 4_000_000_000L)
            throw new IllegalArgumentException("Only explicit fixture matches may be advanced");
        for (int index = 0; index < count; index++) game.tick(game.getContinuous().nextTick);
        repository.save(game);
        return GameResponse.from(game);
    }

    public record Session(UUID gameId, UUID playerId, String token) {}
    public record Fixture(UUID gameId, List<Session> sessions) {}
}
