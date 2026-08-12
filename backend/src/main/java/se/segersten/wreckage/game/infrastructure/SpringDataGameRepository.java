package se.segersten.wreckage.game.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGameRepository extends JpaRepository<GameEntity, Long> {

    Optional<GameEntity> findByDomainId(UUID domainId);
}
