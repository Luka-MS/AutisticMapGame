package mapgame.ui;

import static mapgame.model.World.H;
import static mapgame.model.World.W;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.model.Terrain;
import mapgame.model.World;

/** Paints the province map into a world-sized image. */
public final class MapRenderer {
    private static final int UNCLAIMED = 0xC9C1A8;

    private MapRenderer() {
    }

    public static BufferedImage newImage() {
        return new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    }

    public static void render(World world, BufferedImage img, boolean political) {
        int[] out = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int[] ids = world.ids;
        int n = world.provinces.size();
        int[] base = new int[n], stripe = new int[n];
        for (Province p : world.provinces) {
            if (!p.isLand()) {
                base[p.id] = stripe[p.id] = Terrain.OCEAN.rgb;
            } else if (!political) {
                int shade = 94 + (p.id * 37 % 13);
                base[p.id] = stripe[p.id] = scale(p.terrain.rgb, shade / 100.0);
            } else {
                base[p.id] = blend(p.owner == null ? UNCLAIMED : p.owner.rgb, p.terrain.rgb, 0.18);
                Country ctl = p.controller;
                stripe[p.id] = ctl != null && ctl != p.owner ? blend(ctl.rgb, p.terrain.rgb, 0.18) : base[p.id];
            }
        }

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                int id = ids[i];
                int color = ((x + y) >> 2 & 1) == 0 ? stripe[id] : base[id];
                Province p = world.provinces.get(id);
                if (p.isLand()) {
                    // Terrain texture: strong on the terrain map, subtle on the political map.
                    double tex = texture(p.terrain, x, y);
                    color = scale(color, 1 + (tex - 1) * (political ? 0.45 : 1.0));
                    double f = 1;
                    f = Math.min(f, edge(world, p, x > 0 ? ids[i - 1] : id, political));
                    f = Math.min(f, edge(world, p, x < W - 1 ? ids[i + 1] : id, political));
                    f = Math.min(f, edge(world, p, y > 0 ? ids[i - W] : id, political));
                    f = Math.min(f, edge(world, p, y < H - 1 ? ids[i + W] : id, political));
                    if (f < 1) color = scale(color, f);
                }
                out[i] = color;
            }
        }
        drawRivers(world, img);
    }

    /** Rivers are drawn over the land in a soft blue. */
    private static void drawRivers(World world, BufferedImage img) {
        if (world.rivers.isEmpty()) return;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x4E8FC4));
        for (double[] r : world.rivers) {
            Path2D.Double path = new Path2D.Double();
            for (int k = 0; k < r.length / 2; k++) {
                if (k == 0) path.moveTo(r[0], r[1]);
                else path.lineTo(r[2 * k], r[2 * k + 1]);
            }
            g.draw(path);
        }
        g.dispose();
    }

    /** Brightness factor (about 0.85 - 1.15) of a pixel of the given terrain; deterministic, no state. */
    private static double texture(Terrain t, int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        double noise = ((h ^ (h >>> 16)) & 0xFF) / 255.0 - 0.5;
        return switch (t) {
            case FOREST -> ((x * 7 + y * 13) % 11 == 0 ? 0.80 : 1.0) + noise * 0.07;
            case MOUNTAIN -> (((x - y) >> 2 & 1) == 0 ? 1.10 : 0.88) + noise * 0.06;
            case DESERT -> 1 + 0.06 * Math.sin((x + y * 0.6) / 4.5) + noise * 0.04;
            case SNOW -> 1.04 + noise * 0.04;
            case PLAINS -> 1 + noise * 0.06;
            default -> 1;
        };
    }

    /** Darkening factor for a pixel of {@code p} touching province {@code otherId}. */
    private static double edge(World world, Province p, int otherId, boolean political) {
        if (otherId == p.id) return 1;
        Province o = world.provinces.get(otherId);
        if (!o.isLand()) return 0.55;
        if (political && p.owner != o.owner) return 0.45;
        return 0.82;
    }

    private static int scale(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
