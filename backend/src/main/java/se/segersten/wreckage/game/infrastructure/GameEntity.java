package se.segersten.wreckage.game.infrastructure;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.Player;

@Entity
@Table(name = "game")
class GameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_id", nullable = false, unique = true)
    private UUID domainId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "board_width")
    private Integer boardWidth;

    @Column(name = "board_height")
    private Integer boardHeight;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerEntity> players = new ArrayList<>();

    protected GameEntity() {
    }

    private GameEntity(UUID domainId, Board board) {
        this.domainId = domainId;
        if (board != null) {
            this.boardWidth = board.width();
            this.boardHeight = board.height();
        }
    }

    static GameEntity fromDomain(Game game) {
        GameEntity entity = new GameEntity(game.getId(), game.getBoard());
        entity.addMissingPlayers(game.getPlayers());
        return entity;
    }

    GameEntity updateFrom(Game game) {
        Board board = game.getBoard();
        this.boardWidth = board == null ? null : board.width();
        this.boardHeight = board == null ? null : board.height();
        addMissingPlayers(game.getPlayers());
        return this;
    }

    private void addMissingPlayers(List<Player> domainPlayers) {
        Set<UUID> existingIds = players.stream()
                .map(PlayerEntity::getDomainId)
                .collect(Collectors.toSet());
        domainPlayers.stream()
                .filter(player -> !existingIds.contains(player.getId()))
                .map(player -> PlayerEntity.fromDomain(player, this))
                .forEach(players::add);
    }

    Game toDomain() {
        Board board = boardWidth == null || boardHeight == null
                ? null
                : new Board(boardWidth, boardHeight);
        List<Player> domainPlayers = players.stream()
                .map(PlayerEntity::toDomain)
                .toList();
        return new Game(domainId, domainPlayers, board);
    }
}
