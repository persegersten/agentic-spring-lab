package se.segersten.wreckage.game.application;

import java.time.Instant;

import se.segersten.wreckage.game.domain.Game;

public interface PlayerAutomation {

    boolean addHeadlessPlayer(Game game, Instant now);

    void lockHeadlessPrograms(Game game);
}
