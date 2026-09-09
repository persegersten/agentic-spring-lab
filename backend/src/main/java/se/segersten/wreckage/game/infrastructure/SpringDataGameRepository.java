package se.segersten.wreckage.game.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import se.segersten.wreckage.game.domain.GameStatus;

interface SpringDataGameRepository extends JpaRepository<GameEntity, Long> {

    Optional<GameEntity> findByDomainId(UUID domainId);

    List<GameEntity> findAllByStatus(GameStatus status);
}
