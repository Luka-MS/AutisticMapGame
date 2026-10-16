package mapgame.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.model.Terrain;
import mapgame.sim.Game;
import mapgame.ui.MapRenderer;

/** Headless smoke test: generates a world, runs an AI-only simulation and writes map snapshots. */
public final class SimCheck {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        int years = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        File outDir = new File(args.length > 2 ? args[2] : "out");
        MapKind kind = args.length > 3 ? MapKind.valueOf(args[3].toUpperCase()) : MapKind.RANDOM;
        outDir.mkdirs();

        long t0 = System.currentTimeMillis();
        Game g = Game.create(kind, seed);
        System.out.println("Generated in " + (System.currentTimeMillis() - t0) + " ms: "
                + g.world.provinces.size() + " provinces, " + g.world.countries.size() + " countries");

        Map<Terrain, Integer> terrain = new EnumMap<>(Terrain.class);
        for (Province p : g.world.provinces) terrain.merge(p.terrain, 1, Integer::sum);
        System.out.println("Terrain: " + terrain);
        for (Country c : g.world.countries) {
            if (g.world.countries.size() > 40 && c.provinces.size() < 15) continue;
            System.out.println("  " + c.name + ": " + c.provinces.size() + " provinces, " + g.armiesOf(c).size() + " armies");
        }

        var img = MapRenderer.newImage();
        MapRenderer.render(g.world, img, false);
        ImageIO.write(img, "png", new File(outDir, "terrain.png"));
        MapRenderer.render(g.world, img, true);
        ImageIO.write(img, "png", new File(outDir, "political_start.png"));

        ArrayList<String> log = new ArrayList<>();
        g.logSink = log::add;
        t0 = System.currentTimeMillis();
        for (int d = 0; d < years * 360; d++) {
            g.tickDay();
            if (g.mapDirty) {
                g.mapDirty = false;
                g.world.recomputeLabels();
            }
        }
        System.out.println("Simulated " + years + " years in " + (System.currentTimeMillis() - t0) + " ms");
        System.out.println("Alive countries: " + g.aliveCountries().size() + ", armies: " + g.armies.size());
        long wars = log.stream().filter(s -> s.contains("declared war")).count();
        long peaces = log.stream().filter(s -> s.contains("Peace between")).count();
        System.out.println("Wars declared: " + wars + ", peace treaties: " + peaces);
        log.stream().filter(s -> s.contains("eliminated")).forEach(s -> System.out.println("  " + s));
        for (Country c : g.aliveCountries()) {
            System.out.printf("  %s: %d provinces, gold %.0f, manpower %d, strength %d%n",
                    c.name, c.provinces.size(), c.gold, c.manpower, g.strength(c));
        }
        MapRenderer.render(g.world, img, true);
        ImageIO.write(img, "png", new File(outDir, "political_end.png"));
    }
}
