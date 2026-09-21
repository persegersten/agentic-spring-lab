package se.segersten.wreckage.game.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.GameConfiguration;

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
    public GameResponse createGame(@RequestBody(required = false) CreateGameRequest request) {
        GameConfiguration configuration = request == null
                ? GameConfiguration.defaults()
                : new GameConfiguration(request.maxPlayers(), request.joinTimeoutSeconds(),
                        request.cardsPerRound(), request.planningTimeoutSeconds());
        return GameResponse.from(gameService.createGame(configuration));
    }

    @GetMapping("/configuration/defaults")
    public GameConfiguration getDefaultConfiguration() { return GameConfiguration.defaults(); }

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
    public PlayerJoinResponse addPlayer(
            @Parameter(description = "Game identifier", required = true)
            @PathVariable UUID gameId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Player to add",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AddPlayerRequest.class)))
            @RequestBody AddPlayerRequest request) {
        var join = gameService.addPlayer(gameId, request.name());
        return new PlayerJoinResponse(join.player().getId(), join.player().getName(), join.token());
    }

    @GetMapping("/{gameId}/players/{playerId}")
    public PlayerGameResponse getPlayerGame(@PathVariable UUID gameId, @PathVariable UUID playerId,
            @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return PlayerGameResponse.from(gameService.getPlayerGame(gameId, playerId, token), playerId);
    }

    @PostMapping("/{gameId}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerGameResponse startRound(@PathVariable UUID gameId,
            @RequestHeader("X-Player-Id") UUID playerId,
            @RequestHeader(value = "X-Player-Token", required = false) String token) {
        gameService.startRound(gameId, playerId, token);
        return PlayerGameResponse.from(gameService.getPlayerGame(gameId, playerId, token), playerId);
    }

    @PostMapping("/{gameId}/rounds/current/program")
    public PlayerGameResponse submitProgram(@PathVariable UUID gameId,
            @RequestHeader("X-Player-Id") UUID playerId,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @RequestBody ProgramRequest request) {
        gameService.submitProgram(gameId, playerId, token, request.orders());
        return PlayerGameResponse.from(gameService.getPlayerGame(gameId, playerId, token), playerId);
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

    @GetMapping("/running")
    @Operation(summary = "Get all running games")
    @ApiResponse(
            responseCode = "200",
            description = "Running games",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GameResponse.class))))
    public List<GameResponse> getRunningGames() {
        return gameService.getRunningGames().stream()
                .map(GameResponse::from)
                .toList();
    }

    @GetMapping("/finished")
    @Operation(summary = "Get all finished games")
    @ApiResponse(
            responseCode = "200",
            description = "Finished games",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GameResponse.class))))
    public List<GameResponse> getFinishedGames() {
        return gameService.getFinishedGames().stream()
                .map(GameResponse::from)
                .toList();
    }

    public record AddPlayerRequest(String name) {
    }
    public record CreateGameRequest(int maxPlayers, int joinTimeoutSeconds,
                                    int cardsPerRound, int planningTimeoutSeconds) {}
    public record ProgramRequest(List<MovementOrder> orders) {}
    public record PlayerJoinResponse(UUID id, String name, String token) {}

}
