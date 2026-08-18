package se.segersten.wreckage.game.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class Game {

    private final UUID id;
    private final List<Player> players;
    private final Board board;
    private final Map<Player, Vehicle> vehicles = new HashMap<>();

    public Game(UUID id, Board board) {
        this(id, new ArrayList<>(), board);
    }

    public Game(UUID id, List<Player> players, Board board) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.players = new ArrayList<>(Objects.requireNonNull(players, "players must not be null"));
        this.board = board;
    }

    public UUID getId() {
        return id;
    }

    public List<Player> getPlayers() {
        return List.copyOf(players);
    }

    public Board getBoard() {
        return board;
    }

    public Player addPlayer(String name) {
        Player player = new Player(UUID.randomUUID(), name);
        players.add(player);
        return player;
    }
}
