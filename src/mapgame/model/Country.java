package mapgame.model;

import java.util.ArrayList;
import java.util.List;
import mapgame.i18n.I18n;

public final class Country {
    public final int id;
    public final String name;
    public final int rgb;
    public Province capital;
    public double gold;
    public int manpower;
    public boolean alive = true;
    public final List<Province> provinces = new ArrayList<>();
    /** How many provinces this country tries to claim per growth round during generation. */
    public int growthRate = 1;
    public double labelX, labelY;
    /** AI countries will not start new wars before this day. */
    public int truceUntil;

    /** The name in the current language; invented nations keep their generated name. */
    public String displayName() {
        return I18n.countryName(name);
    }

    public Country(int id, String name, int rgb) {
        this.id = id;
        this.name = name;
        this.rgb = rgb;
    }
}
