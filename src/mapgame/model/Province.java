package mapgame.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Province {
    public final int id;
    public String name;
    /** Centroid in world pixels. */
    public double x, y;
    public Terrain terrain = Terrain.OCEAN;
    public Country owner;
    /** Whoever currently holds the province (differs from owner while occupied). */
    public Country controller;
    public int population;
    public final List<Province> neighbors = new ArrayList<>();
    /** Provinces reachable over a narrow strait (also listed in neighbors); travel takes longer. */
    public final Set<Province> ferryTo = new HashSet<>();
    /** Id of the connected piece of land this province belongs to. */
    public int landmass;
    public int minX, minY, maxX, maxY, pixels;
    public boolean coastal;

    public Province(int id) {
        this.id = id;
    }

    public static final int FERRY_DAYS = 4;

    /** Days an army needs to enter this province coming from {@code from}. */
    public int travelDays(Province from) {
        return terrain.moveDays + (ferryTo.contains(from) ? FERRY_DAYS : 0);
    }

    public boolean isLand() {
        return terrain.land;
    }

    /** Gold per month. */
    public double income() {
        return population / 10000.0;
    }

    /** Manpower per month. */
    public int manpowerGain() {
        return (int) (population * 0.0006);
    }
}
