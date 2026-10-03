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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Column(name = "access_token_hash", nullable = false)
    private String accessTokenHash;

    @Column(name = "automated", nullable = false)
    private boolean automated;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "visited_checkpoints", nullable = false)
    private String visitedCheckpoints;

    @Column(name = "crashes", nullable = false)
    private int crashes;

    protected PlayerEntity() {
    }

    private PlayerEntity(UUID domainId, GameEntity game, String name, String accessTokenHash,
                         boolean automated, int score, String visitedCheckpoints, int crashes) {
        this.domainId = domainId;
        this.game = game;
        this.name = name;
        this.accessTokenHash = accessTokenHash;
        this.automated = automated;
        this.score = score;
        this.visitedCheckpoints = visitedCheckpoints;
        this.crashes = crashes;
    }

    static PlayerEntity fromDomain(Player player, GameEntity game) {
        return new PlayerEntity(player.getId(), game, player.getName(), player.getAccessTokenHash(),
                player.isAutomated(), player.getScore(), encodeCheckpoints(player.getVisitedCheckpoints()), player.getCrashes());
    }

    PlayerEntity updateFrom(Player player) {
        score = player.getScore();
        visitedCheckpoints = encodeCheckpoints(player.getVisitedCheckpoints());
        crashes = player.getCrashes();
        return this;
    }

    UUID getDomainId() {
        return domainId;
    }

    Player toDomain() {
        Set<String> checkpoints = visitedCheckpoints == null || visitedCheckpoints.isBlank() ? Set.of()
                : Arrays.stream(visitedCheckpoints.split("\\|", -1)).collect(Collectors.toUnmodifiableSet());
        return Player.rehydrate(domainId, name, accessTokenHash, automated, score, checkpoints, crashes);
    }

    private static String encodeCheckpoints(Set<String> checkpoints) {
        return checkpoints.stream().sorted().collect(Collectors.joining("|"));
    }
}
