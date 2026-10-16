package mapgame.model;

public enum MapKind {
    RANDOM("Random world", "A freshly generated world with continents and 16 invented nations."),
    EUROPE("Europe", "Real borders of Europe, the Mediterranean, North Africa and the Near East."),
    WORLD("World", "The whole Earth with all of its real nations.");

    public final String label;
    public final String description;

    MapKind(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
