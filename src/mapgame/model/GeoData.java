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
    /** Rings are flat arrays of lon, lat pairs. subs are first-level subdivisions (e.g. German states), possibly empty. */
    record GeoCountry(String name, long population, String code, List<double[]> rings, List<GeoSub> subs) {
    }

    /** whole: the subdivision is exactly one province instead of being cut into several. */
    record GeoSub(String name, boolean whole, List<double[]> rings) {
    }

    /** A city or capital with its real position. */
    record GeoCity(String name, double lon, double lat, long population, boolean capital, String code, String countryName) {
    }

    /** kind: M = mountains, D = desert, U = tundra. */
    record GeoRegion(char kind, List<double[]> rings) {
    }

    final List<GeoCountry> countries = new ArrayList<>();
    final List<GeoRegion> regions = new ArrayList<>();
    final List<GeoCity> cities = new ArrayList<>();
    /** River polylines as flat arrays of lon, lat pairs. */
    final List<double[]> rivers = new ArrayList<>();

    private static GeoData cached;

    static synchronized GeoData load() {
        if (cached == null) cached = read();
        return cached;
    }

    private static GeoData read() {
        GeoData data = new GeoData();
        // A mod can replace the geography completely with its own world.txt (same format, see tools/prepare_data.py).
        java.nio.file.Path mod = java.nio.file.Paths.get("mods", "data", "world.txt");
        try (InputStream in = java.nio.file.Files.isRegularFile(mod) ? java.nio.file.Files.newInputStream(mod)
                : GeoData.class.getResourceAsStream("/mapgame/data/world.txt")) {
            if (in == null) throw new IllegalStateException("Missing resource /mapgame/data/world.txt - run build.ps1");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<double[]> rings = null;
            GeoCountry country = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] f = line.split("\t");
                switch (f[0]) {
                    case "C" -> {
                        rings = new ArrayList<>();
                        country = new GeoCountry(f[1], Long.parseLong(f[2]), f.length > 3 ? f[3] : "", rings, new ArrayList<>());
                        data.countries.add(country);
                    }
                    case "S" -> {
                        rings = new ArrayList<>();
                        country.subs().add(new GeoSub(f[1], f.length > 2 && f[2].equals("whole"), rings));
                    }
                    case "V" -> {
                        String[] t = f[1].split(" ");
                        double[] river = new double[t.length];
                        for (int i = 0; i < t.length; i++) river[i] = Double.parseDouble(t[i]);
                        data.rivers.add(river);
                    }
                    case "P" -> data.cities.add(new GeoCity(f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                            Long.parseLong(f[4]), f[5].equals("1"), f.length > 6 ? f[6] : "", f.length > 7 ? f[7] : ""));
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
