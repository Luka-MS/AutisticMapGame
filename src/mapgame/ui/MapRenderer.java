package mapgame.ui;

import static mapgame.model.World.H;
import static mapgame.model.World.W;

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
