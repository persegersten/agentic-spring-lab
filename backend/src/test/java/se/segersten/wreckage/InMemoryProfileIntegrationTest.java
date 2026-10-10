package se.segersten.wreckage;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.segersten.wreckage.game.domain.*;
import se.segersten.wreckage.game.application.GameService;

@ActiveProfiles("in-memory")
@SpringBootTest
class InMemoryProfileIntegrationTest {
    @Autowired GameRepository repository;
    @Autowired GameService service;

    @Test void persistsShieldConsumptionAndRoundSelection() {
        Instant now=Instant.parse("2026-01-01T12:00:00Z");
        Game game=new Game(UUID.randomUUID(),List.of(),new Board(10,10),GameStatus.WAITING_FOR_PLAYERS,
                Map.of(),null,new GameConfiguration(2,60,5,30),now,now.plusSeconds(60));
        Player a=game.addPlayer("A","a"), b=game.addPlayer("B","b");
        game.start(now,null,Map.of());
        List<MovementOrder> waits=Collections.nCopies(5,MovementOrder.WAIT);
        game.getRound().lock(a.getId(),waits,true); a.consumeShield();
        game.getRound().lock(b.getId(),waits,false);
        repository.save(game);
        Game restored=service.getGame(game.getId());
        assertThat(restored.requirePlayer(a.getId()).isShieldConsumed()).isTrue();
        assertThat(restored.getRound().programs().get(a.getId()).shieldSelected()).isTrue();
    }
}
