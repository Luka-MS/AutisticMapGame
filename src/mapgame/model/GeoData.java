package mapgame.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Real-world geometry (Natural Earth) loaded from the bundled world.txt resource. */
final class GeoData {
    /** Rings are flat arrays of lon, lat pairs. */
    record GeoCountry(String name, long population, List<double[]> rings) {
    }

    /** kind: M = mountains, D = desert, U = tundra. */
    record GeoRegion(char kind, List<double[]> rings) {
    }

    final List<GeoCountry> countries = new ArrayList<>();
    final List<GeoRegion> regions = new ArrayList<>();

    private static GeoData cached;

    static synchronized GeoData load() {
        if (cached == null) cached = read();
        return cached;
    }

    private static GeoData read() {
        GeoData data = new GeoData();
        try (InputStream in = GeoData.class.getResourceAsStream("/mapgame/data/world.txt")) {
            if (in == null) throw new IllegalStateException("Missing resource /mapgame/data/world.txt - run build.ps1");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<double[]> rings = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] f = line.split("\t");
                switch (f[0]) {
                    case "C" -> {
                        rings = new ArrayList<>();
                        data.countries.add(new GeoCountry(f[1], Long.parseLong(f[2]), rings));
                    }
                    case "T" -> {
                        rings = new ArrayList<>();
                        data.regions.add(new GeoRegion(f[1].charAt(0), rings));
                    }
                    case "R" -> {
                        String[] t = f[1].split(" ");
                        double[] ring = new double[t.length];
                        for (int i = 0; i < t.length; i++) ring[i] = Double.parseDouble(t[i]);
                        rings.add(ring);
                    }
                    default -> { }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return data;
    }
}
