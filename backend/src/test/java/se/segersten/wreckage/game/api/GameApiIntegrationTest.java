package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PutMapping;

@ActiveProfiles("in-memory")
@SpringBootTest
class GameApiIntegrationTest {
    @Autowired GameController controller;

    @Test void completeProgrammingFlowNeedsNoLoadoutAndConsumesShield() {
        var created=controller.createGame(null);
        UUID gameId=created.game().id();
        var a=controller.addPlayer(gameId,new GameController.AddPlayerRequest("A"));
        var b=controller.addPlayer(gameId,new GameController.AddPlayerRequest("B"));
        controller.startGame(gameId,created.hostToken());
        var av=controller.getPlayerGame(gameId,a.id(),a.token());
        var bv=controller.getPlayerGame(gameId,b.id(),b.token());
        assertThat(av.round().hand()).hasSize(8);
        controller.submitProgram(gameId,a.id(),a.token(),new GameController.ProgramRequest(av.round().hand().subList(0,5),true));
        assertThat(controller.getPlayerGame(gameId,a.id(),a.token()).shieldStatus()).isEqualTo("SELECTED");
        controller.submitProgram(gameId,b.id(),b.token(),new GameController.ProgramRequest(bv.round().hand().subList(0,5),false));
        var resolved=controller.getPlayerGame(gameId,a.id(),a.token());
        assertThat(resolved.shieldStatus()).isEqualTo("ACTIVE");
        assertThat(resolved.round().state().playback()).extracting(RoundEventResponse::type)
                .contains(se.segersten.wreckage.game.domain.RoundEventType.SHIELD_ACTIVATED);
        controller.startRound(gameId,a.id(),a.token(),1);
        var next=controller.getPlayerGame(gameId,a.id(),a.token());
        assertThat(next.shieldStatus()).isEqualTo("CONSUMED");
        assertThatThrownBy(() -> controller.saveProgramDraft(gameId,a.id(),a.token(),
                new GameController.ProgramRequest(List.of(),true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been consumed");
        assertThat(Arrays.stream(GameController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PutMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PutMapping.class).value())))
                .noneMatch(path -> path.contains("loadout"));
    }
}
