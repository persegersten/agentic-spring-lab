package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import se.segersten.wreckage.game.application.GameService;

@ExtendWith(OutputCaptureExtension.class)
class GameExceptionHandlerTest {

    @Test
    void databaseFailureReturnsServerErrorAndLogsTheUnderlyingCause(CapturedOutput output) throws Exception {
        var service = mock(GameService.class);
        var cause = new SQLException("Check constraint invalid: chk_game_status", "23514");
        when(service.createHostedGame(any())).thenThrow(new DataIntegrityViolationException("Insert failed", cause));
        var mvc = MockMvcBuilders.standaloneSetup(new GameController(service))
                .setControllerAdvice(new GameExceptionHandler()).build();

        mvc.perform(post("/games"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"message":"Could not save game data because of an internal server error."}
                        """));

        assertThat(output.getAll()).contains("Failed to persist game data for POST /games",
                "Insert failed", "java.sql.SQLException: Check constraint invalid: chk_game_status");
    }
}
