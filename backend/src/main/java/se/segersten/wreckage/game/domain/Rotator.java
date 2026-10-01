package se.segersten.wreckage.game.domain;
import java.util.Objects;
public record Rotator(Position position,Rotation rotation){public Rotator{Objects.requireNonNull(position);Objects.requireNonNull(rotation);}}
