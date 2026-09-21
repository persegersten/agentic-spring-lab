package se.segersten.wreckage.game.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LobbyScheduler {

    private final GameService gameService;

    public LobbyScheduler(GameService gameService) {
        this.gameService = gameService;
    }

    @Scheduled(fixedDelay = 500)
    public void startExpiredLobbies() {
        gameService.startExpiredLobbies();
    }
}
