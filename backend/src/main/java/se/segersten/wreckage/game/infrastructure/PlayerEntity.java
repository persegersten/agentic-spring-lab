package se.segersten.wreckage.game.infrastructure;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import se.segersten.wreckage.game.domain.Player;

@Entity
@Table(name = "player")
class PlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_id", nullable = false, unique = true)
    private UUID domainId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "name", nullable = false)
    private String name;

    protected PlayerEntity() {
    }

    private PlayerEntity(UUID domainId, GameEntity game, String name) {
        this.domainId = domainId;
        this.game = game;
        this.name = name;
    }

    static PlayerEntity fromDomain(Player player, GameEntity game) {
        return new PlayerEntity(player.getId(), game, player.getName());
    }

    UUID getDomainId() {
        return domainId;
    }

    Player toDomain() {
        return Player.rehydrate(domainId, name);
    }
}
