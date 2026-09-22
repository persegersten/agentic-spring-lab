package se.segersten.wreckage.game.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository {

    Game save(Game game);

    Optional<Game> findById(UUID id);

    Optional<Game> findByIdForUpdate(UUID id);

    List<Game> findAllByStatus(GameStatus status);

    List<Game> findAllByStatusForUpdate(GameStatus status);
}
