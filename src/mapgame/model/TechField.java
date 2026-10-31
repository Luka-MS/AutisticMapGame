package mapgame.model;

import mapgame.i18n.I18n;

/** The three research fields; every field has {@link #MAX_LEVEL} levels. */
public enum TechField {
    /** +6% damage dealt and -4% damage taken per level. */
    MILITARY,
    /** +8% gold per level. */
    ECONOMY,
    /** +10% manpower and -4% army upkeep per level, and faster research. */
    ADMIN;

    public static final int MAX_LEVEL = 5;

    /** Research points needed to reach the next level from {@code level}. */
    public static int cost(int level) {
        return 12 * (level + 1);
    }

    public String label() {
        return I18n.tr("tech." + name());
    }
}
