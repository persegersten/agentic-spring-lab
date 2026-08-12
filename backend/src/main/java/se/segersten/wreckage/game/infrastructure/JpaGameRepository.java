package se.segersten.wreckage.game.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;

@Repository
public class JpaGameRepository implements GameRepository {

    private final SpringDataGameRepository repository;

    public JpaGameRepository(SpringDataGameRepository repository) {
        this.repository = repository;
    }

    @Override
    public Game save(Game game) {
        GameEntity entity = repository.findByDomainId(game.getId())
                .map(existing -> existing.updateFrom(game))
                .orElseGet(() -> GameEntity.fromDomain(game));
        repository.save(entity);
        return game;
    }

    @Override
    public Optional<Game> findById(UUID id) {
        return repository.findByDomainId(id).map(GameEntity::toDomain);
    }
}
