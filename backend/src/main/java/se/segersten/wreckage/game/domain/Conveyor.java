package se.segersten.wreckage.game.domain;
import java.util.Objects;
public record Conveyor(Position position, Direction direction){public Conveyor{Objects.requireNonNull(position);Objects.requireNonNull(direction);}}
