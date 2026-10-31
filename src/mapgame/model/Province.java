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

    /** Day this province last changed owner through a peace deal; far in the past for the starting borders. */
    public int conqueredDay = NEVER;
    public static final int NEVER = -100_000;
    /** Days a conquered province needs to be fully integrated; it yields only half until then. */
    public static final int INTEGRATION_DAYS = 360;

    /** Share (0.5 - 1.0) of its yield a province delivers {@code day}, lower right after a conquest. */
    public double integration(int day) {
        return 0.5 + 0.5 * Math.min(1.0, Math.max(0, day - conqueredDay) / (double) INTEGRATION_DAYS);
    }

    /** Gold per month on the given day. */
    public double income(int day) {
        return income() * integration(day);
    }

    /** Real city located in this province (real maps only), or null. */
    public String city;
    public long cityPopulation;

    public boolean isCapital() {
        return owner != null && owner.capital == this;
    }

    /** Yield multiplier: cities and especially capitals are worth more than farmland. */
    public double wealth() {
        double w = 1;
        if (city != null) w += cityPopulation >= 5_000_000 ? 0.6 : 0.3;
        if (isCapital()) w += 0.5;
        return w;
    }

    /** Full gold per month, ignoring integration. */
    public double income() {
        return population / 10000.0 * wealth();
    }

    /** Manpower per month. */
    public int manpowerGain() {
        return (int) (population * 0.0006 * wealth());
    }

    /** Manpower per month on the given day. */
    public int manpowerGain(int day) {
        return (int) (manpowerGain() * integration(day));
    }
}
