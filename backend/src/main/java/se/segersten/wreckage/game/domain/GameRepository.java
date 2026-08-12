package se.segersten.wreckage.game.domain;

import java.util.Optional;
import java.util.UUID;

public interface GameRepository {

    Game save(Game game);

    Optional<Game> findById(UUID id);
}
