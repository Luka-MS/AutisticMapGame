package mapgame.model;

public enum Terrain {
    OCEAN("Ocean", 0x2E6F9E, 99, 1.0, false),
    PLAINS("Plains", 0x9BBF6A, 2, 1.0, true),
    FOREST("Forest", 0x4F8A4B, 3, 1.25, true),
    DESERT("Desert", 0xD8C27A, 3, 1.0, true),
    MOUNTAIN("Mountains", 0x8A8478, 5, 1.6, true),
    SNOW("Tundra", 0xE4EBEF, 4, 1.15, true);

    public final String label;
    public final int rgb;
    /** Days an army needs to enter a province of this terrain. */
    public final int moveDays;
    /** Damage reduction for the defender. */
    public final double defense;
    public final boolean land;

    Terrain(String label, int rgb, int moveDays, double defense, boolean land) {
        this.label = label;
        this.rgb = rgb;
        this.moveDays = moveDays;
        this.defense = defense;
        this.land = land;
    }
}
