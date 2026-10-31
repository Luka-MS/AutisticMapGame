package mapgame.model;

import mapgame.i18n.I18n;

/** Things a province can be developed with. Each level takes {@link #BUILD_DAYS} days to build. */
public enum Building {
    /** +20% gold per level. */
    MARKET(60, 3),
    /** +20% manpower per level. */
    BARRACKS(50, 3),
    /** Defenders take 15% less damage per level. */
    FORT(80, 3),
    /** Armies need 15% less time to cross the province per level. */
    ROADS(70, 2);

    public static final int BUILD_DAYS = 60;

    public final int baseCost, maxLevel;

    Building(int baseCost, int maxLevel) {
        this.baseCost = baseCost;
        this.maxLevel = maxLevel;
    }

    /** Gold needed to raise the building from {@code level} to the next one. */
    public int cost(int level) {
        return baseCost * (level + 1);
    }

    public String label() {
        return I18n.tr("building." + name());
    }
}
