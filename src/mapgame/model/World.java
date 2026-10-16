package mapgame.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class World {
    public static final int W = 1920;
    public static final int H = 1080;

    public final long seed;
    /** Province id for every pixel of the world. */
    public final int[] ids;
    public final List<Province> provinces;
    public final List<Country> countries = new ArrayList<>();
    /** Strait crossings between provinces of different landmasses. */
    public final List<Province[]> ferries = new ArrayList<>();

    public World(long seed, int[] ids, List<Province> provinces) {
        this.seed = seed;
        this.ids = ids;
        this.provinces = provinces;
    }

    public Province provinceAt(double wx, double wy) {
        int x = (int) Math.floor(wx), y = (int) Math.floor(wy);
        if (x < 0 || y < 0 || x >= W || y >= H) return null;
        return provinces.get(ids[y * W + x]);
    }

    public void setOwner(Province p, Country c) {
        if (p.owner != null) p.owner.provinces.remove(p);
        p.owner = c;
        p.controller = c;
        if (c != null) c.provinces.add(p);
    }

    /** Places each country's label on its main landmass, on the province closest to the average position. */
    public void recomputeLabels() {
        for (Country c : countries) {
            if (!c.alive || c.provinces.isEmpty()) continue;
            Map<Integer, Integer> sizes = new HashMap<>();
            for (Province p : c.provinces) sizes.merge(p.landmass, 1, Integer::sum);
            int main = c.provinces.get(0).landmass, mainSize = 0;
            for (Map.Entry<Integer, Integer> e : sizes.entrySet()) {
                if (e.getValue() > mainSize) {
                    mainSize = e.getValue();
                    main = e.getKey();
                }
            }
            double ax = 0, ay = 0;
            for (Province p : c.provinces) {
                if (p.landmass != main) continue;
                ax += p.x;
                ay += p.y;
            }
            ax /= mainSize;
            ay /= mainSize;
            Province best = null;
            double bd = Double.MAX_VALUE;
            for (Province p : c.provinces) {
                if (p.landmass != main) continue;
                double d = (p.x - ax) * (p.x - ax) + (p.y - ay) * (p.y - ay);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            c.labelX = best.x;
            c.labelY = best.y;
        }
    }
}
