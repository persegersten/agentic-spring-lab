package se.segersten.wreckage.game.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wreckage.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class LobbyScheduler {

    private final GameService gameService;

    public LobbyScheduler(GameService gameService) {
        this.gameService = gameService;
    }

    @Scheduled(fixedDelay = 500)
    public void completeExpiredPlanning() {
        gameService.completeExpiredPlanning();
    }
}
