package se.segersten.wreckage.game.application;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import se.segersten.wreckage.game.domain.Game;

@Component
@Profile("!headless-players")
public class NoOpPlayerAutomation implements PlayerAutomation {

    @Override
    public void fillLobby(Game game, Instant now) {
    }

    @Override
    public void lockHeadlessPrograms(Game game) {
    }
}
