package mapgame.model;

import mapgame.i18n.I18n;

public enum MapKind {
    RANDOM, EUROPE, WORLD;

    public String label() {
        return I18n.tr("mapkind." + name());
    }

    public String description() {
        return I18n.tr("mapkind." + name() + ".desc");
    }
}
