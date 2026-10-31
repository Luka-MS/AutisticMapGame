package mapgame.tools;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.sim.Game;
import mapgame.sim.Scenario;
import mapgame.ui.MapPanel;

/**
 * Renders screenshots of the map for the README without opening a window.
 * Usage: Screenshots [outDir]  (default docs/screenshots)
 */
public final class Screenshots {
    private static final int W = 1280, H = 800;

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(args.length > 0 ? args[0] : "docs/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);

        Game g = Game.create(MapKind.EUROPE, 1914, Scenario.find("1914"));
        g.player = find(g, "German Empire");
        for (int i = 0; i < 360 * 3; i++) g.tickDay();
        Province focus = battle(g);
        shot(g, focus != null ? focus : g.player.capital, false, new File(dir, "europe-political.png"));
        shot(g, g.player.capital, true, new File(dir, "europe-terrain.png"));

        Game w = Game.create(MapKind.WORLD, 5, null);
        w.player = find(w, "Brazil");
        for (int i = 0; i < 360 * 2; i++) w.tickDay();
        MapPanel map = panel(w);
        map.frame();
        write(map, new File(dir, "world.png"));
        System.out.println("Screenshots written to " + dir.getAbsolutePath());
    }

    private static Country find(Game g, String name) {
        for (Country c : g.world.countries) if (c.alive && c.name.equals(name)) return c;
        throw new IllegalStateException("No nation " + name);
    }

    /** A province with recent fighting, or null. */
    private static Province battle(Game g) {
        Province best = null;
        int bestDay = -1;
        for (Map.Entry<Province, Integer> e : g.recentBattles.entrySet()) {
            if (e.getValue() > bestDay && e.getKey().isLand()) {
                best = e.getKey();
                bestDay = e.getValue();
            }
        }
        return best;
    }

    private static MapPanel panel(Game g) {
        MapPanel map = new MapPanel();
        map.setSize(W, H);
        map.setGame(g);
        map.frame();
        return map;
    }

    private static void shot(Game g, Province focus, boolean terrain, File out) throws IOException {
        MapPanel map = panel(g);
        if (terrain) map.toggleMode();
        map.centerOn(focus);
        map.frame();
        write(map, out);
    }

    private static void write(MapPanel map, File out) throws IOException {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        map.print(g);
        g.dispose();
        ImageIO.write(img, "png", out);
    }
}
