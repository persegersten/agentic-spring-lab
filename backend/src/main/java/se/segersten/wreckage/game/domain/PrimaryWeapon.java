package se.segersten.wreckage.game.domain;

public enum PrimaryWeapon {
    LASER,
    REPULSOR,
    ROCKET;

    public boolean permits(ActionType action) {
        return action != null && action.name().equals(name());
    }
}
