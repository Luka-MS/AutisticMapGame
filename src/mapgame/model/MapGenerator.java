package mapgame.model;

import static mapgame.model.World.H;
import static mapgame.model.World.W;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Procedurally builds a Voronoi province map, terrain and countries. */
public final class MapGenerator {
    private static final int CELL = 40;
    private static final int COUNTRIES = 16;
    private static final double LAND_FRACTION = 0.40;
    private static final int MIN_COUNTRY_LANDMASS = 8;

    private MapGenerator() {
    }

    public static World generate(long seed) {
        Random rnd = new Random(seed);
        int cols = W / CELL, rows = H / CELL, n = cols * rows;

        double[] sx = new double[n], sy = new double[n];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sx[r * cols + c] = (c + 0.15 + 0.7 * rnd.nextDouble()) * CELL;
                sy[r * cols + c] = (r + 0.15 + 0.7 * rnd.nextDouble()) * CELL;
            }
        }

        int[] ids = new int[W * H];
        for (int y = 0; y < H; y++) {
            int cy = y / CELL;
            for (int x = 0; x < W; x++) {
                int cx = x / CELL;
                int best = 0;
                double bd = Double.MAX_VALUE;
                for (int ry = Math.max(0, cy - 2); ry <= Math.min(rows - 1, cy + 2); ry++) {
                    for (int rx = Math.max(0, cx - 2); rx <= Math.min(cols - 1, cx + 2); rx++) {
                        int idx = ry * cols + rx;
                        double dx = sx[idx] - (x + 0.5), dy = sy[idx] - (y + 0.5);
                        double d = dx * dx + dy * dy;
                        if (d < bd) {
                            bd = d;
                            best = idx;
                        }
                    }
                }
                ids[y * W + x] = best;
            }
        }

        List<Province> provinces = buildProvinces(ids, n);

        World world = new World(seed, ids, provinces);
        assignTerrain(world, seed, rnd);
        createCountries(world, rnd);
        return world;
    }

    /** Creates provinces from a per-pixel id map: pixel statistics, centroids and neighbour links. */
    static List<Province> buildProvinces(int[] ids, int n) {
        List<Province> provinces = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Province p = new Province(i);
            p.minX = W;
            p.minY = H;
            provinces.add(p);
        }
        double[] sumX = new double[n], sumY = new double[n];
        Set<Long> edges = new HashSet<>();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int id = ids[y * W + x];
                Province p = provinces.get(id);
                p.pixels++;
                sumX[id] += x + 0.5;
                sumY[id] += y + 0.5;
                p.minX = Math.min(p.minX, x);
                p.maxX = Math.max(p.maxX, x);
                p.minY = Math.min(p.minY, y);
                p.maxY = Math.max(p.maxY, y);
                if (x + 1 < W) addEdge(edges, id, ids[y * W + x + 1], n);
                if (y + 1 < H) addEdge(edges, id, ids[(y + 1) * W + x], n);
            }
        }
        for (Province p : provinces) {
            p.x = sumX[p.id] / p.pixels;
            p.y = sumY[p.id] / p.pixels;
        }
        for (long e : edges) {
            Province a = provinces.get((int) (e / n)), b = provinces.get((int) (e % n));
            a.neighbors.add(b);
            b.neighbors.add(a);
        }
        return provinces;
    }

    private static void addEdge(Set<Long> edges, int a, int b, int n) {
        if (a == b) return;
        edges.add((long) Math.min(a, b) * n + Math.max(a, b));
    }

    private static void assignTerrain(World world, long seed, Random rnd) {
        List<Province> ps = world.provinces;
        int n = ps.size();
        double[] score = new double[n], moist = new double[n];
        for (Province p : ps) {
            double nx = p.x / W, ny = p.y / H;
            double e = fbm(p.x / 520.0, p.y / 520.0, seed);
            double dx = (nx - 0.5) * 2, dy = (ny - 0.5) * 2;
            double edge = Math.pow(Math.hypot(dx, dy) / 1.25, 3) * 0.55;
            score[p.id] = e - edge;
            moist[p.id] = fbm(p.x / 300.0 + 100, p.y / 300.0 + 100, seed + 17);
        }
        double landThreshold = percentile(score, ps, 1 - LAND_FRACTION);
        List<Province> land = new ArrayList<>();
        for (Province p : ps) if (score[p.id] > landThreshold) land.add(p);

        double mountainT = percentile(score, land, 0.88);
        double dryT = percentile(moist, land, 0.20);
        double wetT = percentile(moist, land, 0.65);

        Set<Province> landSet = new HashSet<>(land);
        for (Province p : ps) {
            if (!landSet.contains(p)) {
                p.terrain = Terrain.OCEAN;
                continue;
            }
            double lat = Math.abs(p.y / H - 0.5) * 2;
            if (score[p.id] > mountainT) p.terrain = Terrain.MOUNTAIN;
            else if (lat > 0.82) p.terrain = Terrain.SNOW;
            else if (moist[p.id] < dryT) p.terrain = Terrain.DESERT;
            else if (moist[p.id] > wetT) p.terrain = Terrain.FOREST;
            else p.terrain = Terrain.PLAINS;
        }

        Names names = new Names(rnd);
        for (Province p : ps) {
            p.name = names.province();
            p.population = switch (p.terrain) {
                case PLAINS -> 60_000 + rnd.nextInt(100_000);
                case FOREST -> 30_000 + rnd.nextInt(50_000);
                case DESERT -> 5_000 + rnd.nextInt(25_000);
                case MOUNTAIN -> 5_000 + rnd.nextInt(20_000);
                case SNOW -> 2_000 + rnd.nextInt(8_000);
                case OCEAN -> 0;
            };
        }
        for (Province p : ps) {
            for (Province q : p.neighbors) {
                if (p.isLand() && !q.isLand()) p.coastal = true;
            }
        }
    }

    private static double percentile(double[] values, List<Province> subset, double q) {
        double[] v = new double[subset.size()];
        for (int i = 0; i < v.length; i++) v[i] = values[subset.get(i).id];
        Arrays.sort(v);
        return v[Math.min(v.length - 1, (int) (v.length * q))];
    }

    private static void createCountries(World world, Random rnd) {
        List<Province> land = new ArrayList<>();
        for (Province p : world.provinces) if (p.isLand()) land.add(p);

        List<List<Province>> comps = components(land);
        comps.sort((a, b) -> b.size() - a.size());
        List<Province> capitals = new ArrayList<>();
        List<Province> habitable = new ArrayList<>();
        for (List<Province> comp : comps) {
            if (comp.size() < MIN_COUNTRY_LANDMASS) continue;
            habitable.addAll(comp);
            if (capitals.size() < COUNTRIES) capitals.add(comp.get(rnd.nextInt(comp.size())));
        }
        double minDist = 160;
        int failures = 0;
        while (capitals.size() < COUNTRIES && habitable.size() > capitals.size() && failures < 5000) {
            Province cand = habitable.get(rnd.nextInt(habitable.size()));
            boolean ok = !capitals.contains(cand);
            for (Province c : capitals) {
                if (ok && Math.hypot(c.x - cand.x, c.y - cand.y) < minDist) ok = false;
            }
            if (ok) {
                capitals.add(cand);
            } else if (++failures % 25 == 0) {
                minDist = Math.max(0, minDist - 10);
            }
        }

        Names names = new Names(rnd);
        List<List<Province>> frontiers = new ArrayList<>();
        for (int i = 0; i < capitals.size(); i++) {
            float hue = (float) ((i * 0.618034 + rnd.nextDouble() * 0.05) % 1.0);
            int rgb = Color.HSBtoRGB(hue, 0.5f + rnd.nextFloat() * 0.25f, 0.75f + rnd.nextFloat() * 0.15f) & 0xFFFFFF;
            Country c = new Country(i, names.country(), rgb);
            c.growthRate = 1 + rnd.nextInt(3);
            c.capital = capitals.get(i);
            world.countries.add(c);
            world.setOwner(c.capital, c);
            List<Province> f = new ArrayList<>();
            addUnownedLand(f, c.capital);
            frontiers.add(f);
        }

        boolean any = true;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < world.countries.size(); i++) order.add(i);
        while (any) {
            any = false;
            Collections.shuffle(order, rnd);
            for (int i : order) {
                Country c = world.countries.get(i);
                List<Province> f = frontiers.get(i);
                for (int k = 0; k < c.growthRate && !f.isEmpty(); k++) {
                    while (!f.isEmpty()) {
                        int idx = rnd.nextInt(f.size());
                        Province p = f.get(idx);
                        f.set(idx, f.get(f.size() - 1));
                        f.remove(f.size() - 1);
                        if (p.owner != null) continue;
                        world.setOwner(p, c);
                        addUnownedLand(f, p);
                        break;
                    }
                }
                if (!f.isEmpty()) any = true;
            }
        }
    }

    private static void addUnownedLand(List<Province> frontier, Province p) {
        for (Province q : p.neighbors) if (q.isLand() && q.owner == null) frontier.add(q);
    }

    private static List<List<Province>> components(List<Province> land) {
        Set<Province> seen = new HashSet<>();
        List<List<Province>> result = new ArrayList<>();
        for (Province start : land) {
            if (!seen.add(start)) continue;
            List<Province> comp = new ArrayList<>();
            ArrayDeque<Province> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Province p = queue.poll();
                comp.add(p);
                for (Province q : p.neighbors) if (q.isLand() && seen.add(q)) queue.add(q);
            }
            result.add(comp);
        }
        return result;
    }

    private static double hash(int x, int y, long seed) {
        long h = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double valueNoise(double x, double y, long seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double xf = x - xi, yf = y - yi;
        double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
        double a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    }

    static double fbm(double x, double y, long seed) {
        double sum = 0, amp = 0.5, norm = 0, freq = 1;
        for (int i = 0; i < 5; i++) {
            sum += valueNoise(x * freq, y * freq, seed + i * 101L) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return sum / norm;
    }
}
