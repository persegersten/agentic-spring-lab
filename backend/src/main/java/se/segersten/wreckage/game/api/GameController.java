package se.segersten.wreckage.game.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.Player;

@RestController
@RequestMapping("/games")
@Tag(name = "Games", description = "Create games, add players, and retrieve game state")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a game")
    @ApiResponse(
            responseCode = "201",
            description = "Game created",
            content = @Content(schema = @Schema(implementation = GameResponse.class)))
    public GameResponse createGame() {
        return GameResponse.from(gameService.createGame());
    }

    @PostMapping("/{gameId}/players")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a player to a game")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Player added",
                    content = @Content(schema = @Schema(implementation = PlayerResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid player name",
                    content = @Content(schema = @Schema(implementation = GameExceptionHandler.ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Game not found",
                    content = @Content(schema = @Schema(implementation = GameExceptionHandler.ErrorResponse.class)))
    })
    public PlayerResponse addPlayer(
            @Parameter(description = "Game identifier", required = true)
            @PathVariable UUID gameId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Player to add",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AddPlayerRequest.class)))
            @RequestBody AddPlayerRequest request) {
        Player player = gameService.addPlayer(gameId, request.name());
        return PlayerResponse.from(player);
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Get a game")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current game state",
                    content = @Content(schema = @Schema(implementation = GameResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Game not found",
                    content = @Content(schema = @Schema(implementation = GameExceptionHandler.ErrorResponse.class)))
    })
    public GameResponse getGame(
            @Parameter(description = "Game identifier", required = true)
            @PathVariable UUID gameId) {
        return GameResponse.from(gameService.getGame(gameId));
    }

    public record AddPlayerRequest(String name) {
    }

}
