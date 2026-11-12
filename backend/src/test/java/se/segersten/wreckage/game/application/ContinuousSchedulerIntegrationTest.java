package se.segersten.wreckage.game.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import se.segersten.wreckage.game.domain.MovementOrder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"wreckage.scheduling.enabled=true", "spring.datasource.url=jdbc:h2:mem:continuous-scheduler;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class ContinuousSchedulerIntegrationTest {
    @Autowired GameService service;

    @Test void defaultClockExecutesCommandsWithNoBrowserRequests() throws Exception {
        var hosted=service.createHostedGame();var id=hosted.game().getId();
        var a=service.addPlayer(id,"A");service.addPlayer(id,"B");
        var started=service.startGame(id,hosted.hostToken());
        assertThat(started.getContinuous().intervalMillis).isEqualTo(2000);
        var initial=started.getVehicleStates().getFirst().orientation();
        service.appendCommand(id,a.player().getId(),a.token(),MovementOrder.TURN_RIGHT,UUID.randomUUID());
        // No GET, append or next-round call drives the following four ticks.
        Thread.sleep(10500);
        var after=service.getPlayerGame(id,a.player().getId(),a.token());
        assertThat(after.getContinuous().tick).isGreaterThanOrEqualTo(4);
        assertThat(after.getVehicleStates().getFirst().orientation()).isNotEqualTo(initial);
        assertThat(after.getContinuous().queues.get(a.player().getId()).snapshot()).containsOnlyNulls();
    }
}
