package mapgame.sim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.model.World;

/**
 * A start situation on top of a real map: another start year, merged or renamed nations, changed owners, alliances.
 * Scenarios are plain text files (see {@code res/mapgame/data/scenarios/1914.txt}); extra ones can be dropped into
 * {@code mods/scenarios/}.
 *
 * <pre>
 * # comment
 * name Great Powers 1914
 * start 1914
 * maps europe world
 * merge Austria-Hungary = Austria, Hungary, Czechia      (the first nation absorbs the others and is renamed)
 * rename Turkey = Ottoman Empire
 * newcountry Atlantis 8844AA                              (a new nation with the given RGB colour)
 * owner 123 Atlantis                                      (province 123 now belongs to Atlantis)
 * provname 123 Port Atlantis                              (rename a province)
 * population 123 250000                                   (set a province's population)
 * seed 4711                                               (random map only: the world seed the province ids refer to)
 * alliance France, Russian Empire
 * war Serbia, Austria-Hungary
 * </pre>
 */
public final class Scenario {
    public static final Path MOD_DIR = Paths.get("mods", "scenarios");

    public final String id;
    public String name;
    public int startYear = 1900;
    /** Fixed world seed for the random map (written by the map editor), or null. */
    public Long seed;
    public final Set<MapKind> kinds = EnumSet.noneOf(MapKind.class);
    private final List<String[]> directives = new ArrayList<>();

    private Scenario(String id) {
        this.id = id;
        this.name = id;
    }

    // ---------------------------------------------------------------- loading

    /** All scenarios: the bundled ones, then the files in {@code mods/scenarios}. */
    public static List<Scenario> all() {
        List<Scenario> out = new ArrayList<>();
        try (InputStream in = Scenario.class.getResourceAsStream("/mapgame/data/scenarios/index.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String id;
                while ((id = r.readLine()) != null) {
                    id = id.trim();
                    if (id.isEmpty() || id.startsWith("#")) continue;
                    try (InputStream f = Scenario.class.getResourceAsStream("/mapgame/data/scenarios/" + id + ".txt")) {
                        if (f != null) out.add(parse(id, new InputStreamReader(f, StandardCharsets.UTF_8)));
                    }
                }
            }
        } catch (IOException ignored) {
            // no bundled scenarios
        }
        if (Files.isDirectory(MOD_DIR)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(MOD_DIR, "*.txt")) {
                List<Path> sorted = new ArrayList<>();
                files.forEach(sorted::add);
                sorted.sort(null);
                for (Path p : sorted) {
                    String file = p.getFileName().toString();
                    String id = file.substring(0, file.length() - 4);
                    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        out.removeIf(s -> s.id.equals(id));
                        out.add(parse(id, r));
                    } catch (IOException | RuntimeException ex) {
                        System.err.println("Cannot read scenario " + p + ": " + ex);
                    }
                }
            } catch (IOException ignored) {
                // unreadable mod folder
            }
        }
        return out;
    }

    public static Scenario find(String id) {
        if (id == null) return null;
        for (Scenario s : all()) if (s.id.equals(id)) return s;
        return null;
    }

    /** For tests and tools: parses scenario text. */
    public static Scenario parseForTest(String id, String text) throws IOException {
        return parse(id, new java.io.StringReader(text));
    }

    static Scenario parse(String id, Reader reader) throws IOException {
        Scenario s = new Scenario(id);
        BufferedReader r = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        String line;
        while ((line = r.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int sp = line.indexOf(' ');
            String cmd = (sp < 0 ? line : line.substring(0, sp)).toLowerCase();
            String rest = sp < 0 ? "" : line.substring(sp + 1).strip();
            switch (cmd) {
                case "name" -> s.name = rest;
                case "start" -> s.startYear = Integer.parseInt(rest);
                case "seed" -> s.seed = Long.parseLong(rest);
                case "maps" -> {
                    for (String m : rest.split("[ ,]+")) {
                        if (!m.isEmpty()) s.kinds.add(MapKind.valueOf(m.toUpperCase()));
                    }
                }
                default -> s.directives.add(new String[] {cmd, rest});
            }
        }
        return s;
    }

    // ---------------------------------------------------------------- queries

    public boolean supports(MapKind kind) {
        return kinds.isEmpty() || kinds.contains(kind);
    }

    /** The name in the current language if the texts define one ({@code scenario.<id>}), otherwise the file's name. */
    public String label() {
        String key = "scenario." + id;
        String text = I18n.tr(key);
        return text.equals(key) ? name : text;
    }

    @Override
    public String toString() {
        return label();
    }

    // ---------------------------------------------------------------- applying

    private static Country find(World w, String name) {
        for (Country c : w.countries) if (c.alive && c.name.equals(name.strip())) return c;
        return null;
    }

    private static List<String> list(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split(",")) if (!part.isBlank()) out.add(part.strip());
        return out;
    }

    /** Changes the nations and borders of a freshly generated world. */
    void applyToWorld(World w) {
        for (String[] d : directives) {
            switch (d[0]) {
                case "merge" -> {
                    int eq = d[1].indexOf('=');
                    if (eq < 0) break;
                    String newName = d[1].substring(0, eq).strip();
                    List<String> members = list(d[1].substring(eq + 1));
                    if (members.isEmpty()) break;
                    Country host = find(w, members.get(0));
                    if (host == null) break;
                    for (String m : members.subList(1, members.size())) {
                        Country c = find(w, m);
                        if (c == null || c == host) continue;
                        for (Province p : new ArrayList<>(c.provinces)) w.setOwner(p, host);
                        c.alive = false;
                        c.capital = null;
                    }
                    host.name = newName;
                }
                case "rename" -> {
                    int eq = d[1].indexOf('=');
                    Country c = eq < 0 ? null : find(w, d[1].substring(0, eq));
                    if (c != null) c.name = d[1].substring(eq + 1).strip();
                }
                case "newcountry" -> {
                    int sp = d[1].lastIndexOf(' ');
                    if (sp < 0) break;
                    int rgb = Integer.parseInt(d[1].substring(sp + 1).strip(), 16) & 0xFFFFFF;
                    Country c = new Country(w.countries.size(), d[1].substring(0, sp).strip(), rgb);
                    w.countries.add(c);
                }
                case "owner" -> {
                    int sp = d[1].indexOf(' ');
                    if (sp < 0) break;
                    int id = Integer.parseInt(d[1].substring(0, sp));
                    Country c = find(w, d[1].substring(sp + 1));
                    if (c != null && id >= 0 && id < w.provinces.size() && w.provinces.get(id).isLand()) w.setOwner(w.provinces.get(id), c);
                }
                case "provname" -> {
                    int sp = d[1].indexOf(' ');
                    if (sp < 0) break;
                    int id = Integer.parseInt(d[1].substring(0, sp));
                    if (id >= 0 && id < w.provinces.size()) w.provinces.get(id).name = d[1].substring(sp + 1).strip();
                }
                case "population" -> {
                    int sp = d[1].indexOf(' ');
                    if (sp < 0) break;
                    int id = Integer.parseInt(d[1].substring(0, sp));
                    if (id >= 0 && id < w.provinces.size()) w.provinces.get(id).population = Math.max(0, Integer.parseInt(d[1].substring(sp + 1).strip()));
                }
                default -> { }
            }
        }
        // Keep the world consistent: empty nations are gone, capitals sit in their own land.
        for (Country c : w.countries) {
            if (!c.alive) continue;
            if (c.provinces.isEmpty()) {
                c.alive = false;
                continue;
            }
            if (c.capital == null || c.capital.owner != c) {
                Province best = c.provinces.get(0);
                for (Province p : c.provinces) if (p.population > best.population) best = p;
                c.capital = best;
            }
        }
    }

    /** Diplomatic starting situation: alliances and wars. */
    void applyToGame(Game g) {
        for (String[] d : directives) {
            List<String> names = list(d[1]);
            if (names.size() != 2) continue;
            Country a = find(g.world, names.get(0)), b = find(g.world, names.get(1));
            if (a == null || b == null) continue;
            if (d[0].equals("alliance")) g.makeAlliance(a, b);
            else if (d[0].equals("war")) g.declareWar(a, b);
        }
    }
}
