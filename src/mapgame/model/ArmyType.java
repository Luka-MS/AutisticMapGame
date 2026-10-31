package mapgame.model;

import mapgame.i18n.I18n;

/** Kinds of armies: every army consists of one kind of troops. */
public enum ArmyType {
    // speed (provinces progress per day), attack, vulnerability (damage taken), gold cost, upkeep, map tag
    INFANTRY(1.0, 1.0, 1.0, 1.0, 1.0, "I"),
    CAVALRY(1.5, 1.0, 1.2, 1.5, 1.3, "C"),
    ARTILLERY(0.75, 1.5, 1.3, 2.0, 1.6, "A");

    public final double speed, attack, vulnerability, goldFactor, upkeepFactor;
    public final String tag;

    ArmyType(double speed, double attack, double vulnerability, double goldFactor, double upkeepFactor, String tag) {
        this.speed = speed;
        this.attack = attack;
        this.vulnerability = vulnerability;
        this.goldFactor = goldFactor;
        this.upkeepFactor = upkeepFactor;
        this.tag = tag;
    }

    /** Attack multiplier on the given terrain: horsemen like open ground, guns are hard to use in rough land. */
    public double attackOn(Terrain t) {
        return switch (this) {
            case CAVALRY -> switch (t) {
                case PLAINS, DESERT -> attack * 1.25;
                case FOREST, MOUNTAIN -> attack * 0.8;
                default -> attack;
            };
            case ARTILLERY -> t == Terrain.MOUNTAIN || t == Terrain.FOREST ? attack * 0.85 : attack;
            default -> attack;
        };
    }

    public String label() {
        return I18n.tr("unit." + name());
    }
}
