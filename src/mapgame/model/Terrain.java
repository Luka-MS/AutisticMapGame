package mapgame.model;

import mapgame.i18n.I18n;

public enum Terrain {
    OCEAN(0x2E6F9E, 99, 1.0, false, 0),
    PLAINS(0x9BBF6A, 2, 1.0, true, 0.004),
    FOREST(0x4F8A4B, 3, 1.25, true, 0.006),
    DESERT(0xD8C27A, 3, 1.0, true, 0.015),
    MOUNTAIN(0x8A8478, 5, 1.6, true, 0.012),
    SNOW(0xE4EBEF, 4, 1.15, true, 0.015);

    public final int rgb;
    /** Days an army needs to enter a province of this terrain. */
    public final int moveDays;
    /** Damage reduction for the defender. */
    public final double defense;
    public final boolean land;
    /** Share of its men an unsupplied army loses per 5 days in this terrain (before distance, size and winter). */
    public final double attrition;

    Terrain(int rgb, int moveDays, double defense, boolean land, double attrition) {
        this.rgb = rgb;
        this.moveDays = moveDays;
        this.defense = defense;
        this.land = land;
        this.attrition = attrition;
    }

    public String label() {
        return I18n.tr("terrain." + name());
    }
}
