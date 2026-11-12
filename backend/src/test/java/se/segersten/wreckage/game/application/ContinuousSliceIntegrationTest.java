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
        assertThat(json.readTree(accepted.body()).path("continuous").path("queue").get(3).asText()).isEqualTo("TURN_RIGHT");
        var other=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/games/"+id+"/players/"+b.player().getId()))
                .header("X-Player-Token",b.token()).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(other.statusCode()).isEqualTo(200);
        assertThat(json.readTree(other.body()).path("continuous").path("queue").get(3).isNull()).isTrue();
        service.appendCommand(id,b.player().getId(),b.token(),MovementOrder.TURN_LEFT,UUID.randomUUID());
        service.appendCommand(id,a.player().getId(),a.token(),MovementOrder.FORWARD_1,UUID.randomUUID());
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
