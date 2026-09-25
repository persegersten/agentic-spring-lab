package se.segersten.wreckage.game.application;

import java.time.Instant;

import se.segersten.wreckage.game.domain.Game;

public interface PlayerAutomation {

    void fillLobby(Game game, Instant now);

    void lockHeadlessPrograms(Game game);
}
