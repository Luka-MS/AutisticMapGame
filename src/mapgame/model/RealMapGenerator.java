package mapgame.model;

import static mapgame.model.World.H;
import static mapgame.model.World.W;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a playable world from real geography: countries are rasterised, split into
 * connected pieces of land/sea and cut into Voronoi provinces that follow the real borders.
 */
public final class RealMapGenerator {
    private static final int MIN_LAND_PIECE = 20;
    private static final int MIN_SEA_PIECE = 12;
    private static final int MIN_PROVINCE = 14;
    private static final int MAX_CROSSINGS = 2;

    /** lon0/lat0: map centre, scale: pixels per radian, cell/seaCell: province size, strait: ferry range in pixels. */
    private record Spec(double lon0, double lat0, double scale, int cell, int seaCell, int strait) {
    }

    private RealMapGenerator() {
    }

    public static World generate(MapKind kind, long seed) {
        Spec spec = switch (kind) {
            case EUROPE -> new Spec(18, 55, 880, 26, 64, 30);
            case WORLD -> new Spec(0, 30, 305.6, 24, 64, 12);
            default -> throw new IllegalArgumentException("Not a real map: " + kind);
        };
        GeoData geo = GeoData.load();
        Random rnd = new Random(seed);
        Projection pr = new Projection(spec);

        int[] cls = rasterCountries(geo, pr);
        int[] comp = new int[W * H];
        int pieces = cleanAndLabel(cls, comp);

        List<double[]> seeds = placeSeeds(cls, comp, pieces, spec, rnd);
        int[] ids = new int[W * H];
        assign(seeds, comp, ids, spec.cell);
        seeds = dropTinyProvinces(seeds, comp, pieces, ids, spec.cell);

        int n = seeds.size();
        List<Province> provinces = MapGenerator.buildProvinces(ids, n);
        for (Province p : provinces) {
            double[] s = seeds.get(p.id);
            int idx = (int) s[1] * W + (int) s[0];
            p.landmass = comp[idx];
            p.terrain = cls[idx] == 0 ? Terrain.OCEAN : Terrain.PLAINS;
        }
        World world = new World(seed, ids, provinces);
        int[] provCls = new int[n];
        for (Province p : provinces) {
            double[] s = seeds.get(p.id);
            provCls[p.id] = cls[(int) s[1] * W + (int) s[0]];
        }

        assignTerrain(world, geo, pr, ids, cls, seed);
        createCountries(world, geo, provCls, rnd);
        linkStraits(world, cls, comp, ids, spec.strait);
        for (Province p : provinces) {
            for (Province q : p.neighbors) if (p.isLand() && !q.isLand()) p.coastal = true;
        }
        return world;
    }

    // ---------------------------------------------------------------- projection

    private static final class Projection {
        final double lon0, merc0, scale;

        Projection(Spec s) {
            lon0 = s.lon0;
            merc0 = merc(s.lat0);
            scale = s.scale;
        }

        static double merc(double lat) {
            double l = Math.max(-85, Math.min(85, lat));
            return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(l) / 2));
        }

        double x(double lon) {
            return W / 2.0 + Math.toRadians(lon - lon0) * scale;
        }

        double y(double lat) {
            return H / 2.0 - (merc(lat) - merc0) * scale;
        }

        double lat(double py) {
            return Math.toDegrees(Math.atan(Math.sinh(merc0 - (py - H / 2.0) / scale)));
        }
    }

    private static Path2D.Double path(List<double[]> rings, Projection pr) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (double[] r : rings) {
            for (int k = 0; k < r.length / 2; k++) {
                double x = pr.x(r[2 * k]), y = pr.y(r[2 * k + 1]);
                if (k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.closePath();
        }
        return path;
    }

    private static Graphics2D plainGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(new BasicStroke(1.2f));
        return g;
    }

    /** One value per pixel: 0 = sea, otherwise country index + 1. */
    private static int[] rasterCountries(GeoData geo, Projection pr) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plainGraphics(img);
        List<Integer> order = new ArrayList<>();
        Path2D.Double[] paths = new Path2D.Double[geo.countries.size()];
        double[] area = new double[paths.length];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = path(geo.countries.get(i).rings(), pr);
            var b = paths[i].getBounds2D();
            area[i] = b.getWidth() * b.getHeight();
            order.add(i);
        }
        // Big countries first so that small ones (enclaves) are painted on top. The outline also closes 1px gaps.
        order.sort(Comparator.comparingDouble((Integer i) -> area[i]).reversed());
        for (int i : order) {
            g.setColor(new Color(i + 1));
            g.fill(paths[i]);
            g.draw(paths[i]);
        }
        g.dispose();
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int[] cls = new int[W * H];
        for (int i = 0; i < cls.length; i++) cls[i] = data[i] & 0xFFFFFF;
        return cls;
    }

    // ---------------------------------------------------------------- connected pieces

    /** Labels 4-connected pieces of equal class; returns their number. */
    private static int label(int[] cls, int[] comp) {
        Arrays.fill(comp, -1);
        int[] stack = new int[W * H];
        int count = 0;
        for (int start = 0; start < comp.length; start++) {
            if (comp[start] != -1) continue;
            int sp = 0;
            stack[sp++] = start;
            comp[start] = count;
            while (sp > 0) {
                int j = stack[--sp];
                int x = j % W, y = j / W;
                if (x > 0 && comp[j - 1] == -1 && cls[j - 1] == cls[j]) {
                    comp[j - 1] = count;
                    stack[sp++] = j - 1;
                }
                if (x < W - 1 && comp[j + 1] == -1 && cls[j + 1] == cls[j]) {
                    comp[j + 1] = count;
                    stack[sp++] = j + 1;
                }
                if (y > 0 && comp[j - W] == -1 && cls[j - W] == cls[j]) {
                    comp[j - W] = count;
                    stack[sp++] = j - W;
                }
                if (y < H - 1 && comp[j + W] == -1 && cls[j + W] == cls[j]) {
                    comp[j + W] = count;
                    stack[sp++] = j + W;
                }
            }
            count++;
        }
        return count;
    }

    /** Removes specks (tiny islands, tiny holes) and labels the connected pieces. */
    private static int cleanAndLabel(int[] cls, int[] comp) {
        int count = label(cls, comp);
        int[] size = new int[count];
        for (int c : comp) size[c]++;
        boolean changed = false;
        for (int i = 0; i < cls.length; i++) {
            int c = comp[i];
            if (cls[i] != 0) {
                if (size[c] < MIN_LAND_PIECE) {
                    cls[i] = 0;
                    changed = true;
                }
            } else if (size[c] < MIN_SEA_PIECE) {
                int x = i % W, y = i / W, repl = 0;
                if (x > 0 && cls[i - 1] != 0) repl = cls[i - 1];
                else if (x < W - 1 && cls[i + 1] != 0) repl = cls[i + 1];
                else if (y > 0 && cls[i - W] != 0) repl = cls[i - W];
                else if (y < H - 1 && cls[i + W] != 0) repl = cls[i + W];
                if (repl != 0) {
                    cls[i] = repl;
                    changed = true;
                }
            }
        }
        return changed ? label(cls, comp) : count;
    }

    // ---------------------------------------------------------------- provinces

    /** Seeds are {x, y, piece}. Every piece of land or sea gets at least one. */
    private static List<double[]> placeSeeds(int[] cls, int[] comp, int pieces, Spec spec, Random rnd) {
        List<double[]> seeds = new ArrayList<>();
        boolean[] has = new boolean[pieces];
        for (boolean sea : new boolean[] {false, true}) {
            int cell = sea ? spec.seaCell : spec.cell;
            for (int gy = 0; gy * cell < H; gy++) {
                for (int gx = 0; gx * cell < W; gx++) {
                    double x = (gx + 0.15 + 0.7 * rnd.nextDouble()) * cell, y = (gy + 0.15 + 0.7 * rnd.nextDouble()) * cell;
                    if (x >= W || y >= H) continue;
                    int idx = (int) y * W + (int) x;
                    if ((cls[idx] == 0) != sea) continue;
                    seeds.add(new double[] {x, y, comp[idx]});
                    has[comp[idx]] = true;
                }
            }
        }
        double[] sumX = new double[pieces], sumY = new double[pieces];
        int[] count = new int[pieces], first = new int[pieces];
        Arrays.fill(first, -1);
        for (int i = 0; i < comp.length; i++) {
            int c = comp[i];
            if (first[c] < 0) first[c] = i;
            sumX[c] += i % W;
            sumY[c] += i / W;
            count[c]++;
        }
        for (int c = 0; c < pieces; c++) {
            if (has[c]) continue;
            int cx = (int) (sumX[c] / count[c]), cy = (int) (sumY[c] / count[c]);
            int idx = comp[cy * W + cx] == c ? cy * W + cx : first[c];
            seeds.add(new double[] {idx % W + 0.5, idx / W + 0.5, c});
        }
        return seeds;
    }

    /** Spatial index answering "nearest seed inside the same piece". */
    private static final class SeedIndex {
        private final int cell, gw, gh;
        private final int[] start, items;
        private final double[] sx, sy;
        private final int[] piece;

        SeedIndex(List<double[]> seeds, int cell) {
            this.cell = cell;
            gw = (W + cell - 1) / cell;
            gh = (H + cell - 1) / cell;
            int n = seeds.size();
            sx = new double[n];
            sy = new double[n];
            piece = new int[n];
            start = new int[gw * gh + 1];
            int[] cellOf = new int[n];
            for (int i = 0; i < n; i++) {
                double[] s = seeds.get(i);
                sx[i] = s[0];
                sy[i] = s[1];
                piece[i] = (int) s[2];
                cellOf[i] = Math.min(gh - 1, (int) s[1] / cell) * gw + Math.min(gw - 1, (int) s[0] / cell);
                start[cellOf[i] + 1]++;
            }
            for (int i = 0; i < gw * gh; i++) start[i + 1] += start[i];
            items = new int[n];
            int[] fill = Arrays.copyOf(start, gw * gh);
            for (int i = 0; i < n; i++) items[fill[cellOf[i]]++] = i;
        }

        int nearest(int px, int py, int pc) {
            double x = px + 0.5, y = py + 0.5;
            int cx = px / cell, cy = py / cell;
            int best = -1;
            double bd = Double.MAX_VALUE;
            int maxR = Math.max(gw, gh);
            for (int r = 0; r <= maxR; r++) {
                int x0 = cx - r, x1 = cx + r, y0 = cy - r, y1 = cy + r;
                for (int gy = Math.max(0, y0); gy <= Math.min(gh - 1, y1); gy++) {
                    int step = (gy == y0 || gy == y1) ? 1 : Math.max(1, x1 - x0);
                    for (int gx = x0; gx <= x1; gx += step) {
                        if (gx < 0 || gx >= gw) continue;
                        int c = gy * gw + gx;
                        for (int k = start[c]; k < start[c + 1]; k++) {
                            int s = items[k];
                            if (piece[s] != pc) continue;
                            double dx = sx[s] - x, dy = sy[s] - y, d = dx * dx + dy * dy;
                            if (d < bd) {
                                bd = d;
                                best = s;
                            }
                        }
                    }
                }
                if (best >= 0) {
                    double lim = (double) r * cell;
                    if (lim * lim >= bd) break;
                }
            }
            return best;
        }
    }

    private static void assign(List<double[]> seeds, int[] comp, int[] ids, int cell) {
        SeedIndex index = new SeedIndex(seeds, cell);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) ids[y * W + x] = index.nearest(x, y, comp[y * W + x]);
        }
    }

    /** Removes seeds whose province came out tiny (keeps the biggest one of every piece) and reassigns. */
    private static List<double[]> dropTinyProvinces(List<double[]> seeds, int[] comp, int pieces, int[] ids, int cell) {
        int[] count = new int[seeds.size()];
        for (int id : ids) count[id]++;
        int[] biggest = new int[pieces];
        Arrays.fill(biggest, -1);
        for (int i = 0; i < seeds.size(); i++) {
            int c = (int) seeds.get(i)[2];
            if (biggest[c] < 0 || count[i] > count[biggest[c]]) biggest[c] = i;
        }
        List<double[]> kept = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            if (count[i] >= MIN_PROVINCE || biggest[(int) seeds.get(i)[2]] == i) kept.add(seeds.get(i));
        }
        if (kept.size() != seeds.size()) assign(kept, comp, ids, cell);
        return kept;
    }

    // ---------------------------------------------------------------- terrain, countries, straits

    private static void assignTerrain(World world, GeoData geo, Projection pr, int[] ids, int[] cls, long seed) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plainGraphics(img);
        int[][] order = {{'D', 0x00FF00}, {'U', 0xFF0000}, {'M', 0x0000FF}};
        for (int[] o : order) {
            g.setColor(new Color(o[1]));
            for (GeoData.GeoRegion r : geo.regions) if (r.kind() == o[0]) g.fill(path(r.rings(), pr));
        }
        g.dispose();
        int[] mask = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

        int n = world.provinces.size();
        int[] mtn = new int[n], des = new int[n], tun = new int[n];
        for (int i = 0; i < ids.length; i++) {
            if (cls[i] == 0) continue;
            int rgb = mask[i] & 0xFFFFFF;
            if ((rgb & 0xFF) != 0) mtn[ids[i]]++;
            else if ((rgb & 0xFF00) != 0) des[ids[i]]++;
            else if ((rgb & 0xFF0000) != 0) tun[ids[i]]++;
        }
        for (Province p : world.provinces) {
            if (!p.isLand()) continue;
            double lat = Math.abs(pr.lat(p.y));
            double px = Math.max(1, p.pixels);
            if (mtn[p.id] / px >= 0.30) p.terrain = Terrain.MOUNTAIN;
            else if (tun[p.id] / px >= 0.5 || lat >= 68) p.terrain = Terrain.SNOW;
            else if (des[p.id] / px >= 0.5) p.terrain = Terrain.DESERT;
            else {
                double u = Math.max(0, Math.min(1, (MapGenerator.fbm(p.x / 140, p.y / 140, seed + 3) - 0.3) / 0.4));
                double forest = lat < 12 ? 0.6 : lat < 30 ? 0.15 : lat < 46 ? 0.35 : lat < 66 ? 0.6 : 0.1;
                p.terrain = u < forest ? Terrain.FOREST : Terrain.PLAINS;
            }
        }
    }

    private static double terrainWeight(Terrain t) {
        return switch (t) {
            case PLAINS -> 1.0;
            case FOREST -> 0.7;
            case DESERT -> 0.12;
            case MOUNTAIN -> 0.2;
            case SNOW -> 0.05;
            case OCEAN -> 0;
        };
    }

    private static void createCountries(World world, GeoData geo, int[] provCls, Random rnd) {
        Map<Integer, List<Province>> byCountry = new HashMap<>();
        for (Province p : world.provinces) {
            if (p.isLand()) byCountry.computeIfAbsent(provCls[p.id], k -> new ArrayList<>()).add(p);
        }
        Names names = new Names(rnd);
        for (Province p : world.provinces) p.name = names.province();

        List<Integer> order = new ArrayList<>(byCountry.keySet());
        order.sort(Comparator.comparingInt((Integer k) -> -byCountry.get(k).size()).thenComparingInt(k -> k));
        int idx = 0;
        for (int key : order) {
            GeoData.GeoCountry gc = geo.countries.get(key - 1);
            List<Province> provs = byCountry.get(key);

            double popM = Math.max(0.05, gc.population() / 1e6);
            double total = 150_000 * Math.pow(popM, 0.55);
            double weight = 0;
            for (Province p : provs) weight += p.pixels * terrainWeight(p.terrain);
            for (Province p : provs) {
                p.population = Math.max(2_000, (int) (total * p.pixels * terrainWeight(p.terrain) / Math.max(1, weight)));
            }

            float hue = (float) ((idx * 0.618034 + rnd.nextDouble() * 0.04) % 1.0);
            int rgb = Color.HSBtoRGB(hue, 0.45f + rnd.nextFloat() * 0.3f, 0.72f + rnd.nextFloat() * 0.18f) & 0xFFFFFF;
            Country c = new Country(idx++, gc.name(), rgb);
            world.countries.add(c);
            for (Province p : provs) world.setOwner(p, c);
            c.capital = pickCapital(provs);
        }
    }

    /** The province closest to the centre of the country's biggest landmass. */
    private static Province pickCapital(List<Province> provs) {
        Map<Integer, Integer> sizes = new HashMap<>();
        for (Province p : provs) sizes.merge(p.landmass, 1, Integer::sum);
        int main = provs.get(0).landmass, best = 0;
        for (Map.Entry<Integer, Integer> e : sizes.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                main = e.getKey();
            }
        }
        double ax = 0, ay = 0;
        for (Province p : provs) {
            if (p.landmass != main) continue;
            ax += p.x;
            ay += p.y;
        }
        ax /= best;
        ay /= best;
        Province cap = null;
        double bd = Double.MAX_VALUE;
        for (Province p : provs) {
            if (p.landmass != main) continue;
            double d = (p.x - ax) * (p.x - ax) + (p.y - ay) * (p.y - ay);
            if (d < bd) {
                bd = d;
                cap = p;
            }
        }
        return cap;
    }

    /** Connects provinces of different landmasses that face each other across a narrow strait. */
    private static void linkStraits(World world, int[] cls, int[] comp, int[] ids, int range) {
        int n = world.provinces.size();
        Map<Long, Integer> closest = new HashMap<>();
        int r2 = range * range;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (cls[i] == 0 || ((x + y) & 1) != 0) continue;
                boolean coast = (x > 0 && cls[i - 1] == 0) || (x < W - 1 && cls[i + 1] == 0)
                        || (y > 0 && cls[i - W] == 0) || (y < H - 1 && cls[i + W] == 0);
                if (!coast) continue;
                for (int dy = -range; dy <= range; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= H) continue;
                    for (int dx = -range; dx <= range; dx++) {
                        int xx = x + dx, d2 = dx * dx + dy * dy;
                        if (xx < 0 || xx >= W || d2 > r2) continue;
                        int j = yy * W + xx;
                        if (cls[j] == 0 || comp[j] == comp[i]) continue;
                        int a = ids[i], b = ids[j];
                        closest.merge((long) Math.min(a, b) * n + Math.max(a, b), d2, Math::min);
                    }
                }
            }
        }
        // Per pair of landmasses keep only the few shortest crossings to avoid a web of links.
        List<Map.Entry<Long, Integer>> pairs = new ArrayList<>(closest.entrySet());
        pairs.sort(Map.Entry.comparingByValue());
        Map<Long, Integer> perIslandPair = new HashMap<>();
        for (Map.Entry<Long, Integer> e : pairs) {
            Province pa = world.provinces.get((int) (e.getKey() / n)), pb = world.provinces.get((int) (e.getKey() % n));
            long islands = (long) Math.min(pa.landmass, pb.landmass) * (comp.length + 1) + Math.max(pa.landmass, pb.landmass);
            if (perIslandPair.merge(islands, 1, Integer::sum) > MAX_CROSSINGS) continue;
            pa.neighbors.add(pb);
            pb.neighbors.add(pa);
            pa.ferryTo.add(pb);
            pb.ferryTo.add(pa);
            world.ferries.add(new Province[] {pa, pb});
        }
    }
}
