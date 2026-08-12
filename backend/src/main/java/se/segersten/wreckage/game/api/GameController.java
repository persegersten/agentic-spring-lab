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

import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.Player;

@RestController
@RequestMapping("/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse createGame() {
        return GameResponse.from(gameService.createGame());
    }

    @PostMapping("/{gameId}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerResponse addPlayer(
            @PathVariable UUID gameId,
            @RequestBody AddPlayerRequest request) {
        Player player = gameService.addPlayer(gameId, request.name());
        return PlayerResponse.from(player);
    }

    @GetMapping("/{gameId}")
    public GameResponse getGame(@PathVariable UUID gameId) {
        return GameResponse.from(gameService.getGame(gameId));
    }

    public record AddPlayerRequest(String name) {
    }

}
