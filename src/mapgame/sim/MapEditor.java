package mapgame.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;

/**
 * The model behind the map editor: a world that can be changed (owners, new and renamed nations, province names and
 * populations) and exported as a {@link Scenario} text file for the {@code mods/scenarios} folder.
 */
public final class MapEditor {
    public final Game game;
    public final MapKind kind;
    public final long seed;
    private final int[] originalOwner, originalPopulation;
    private final String[] originalProvinceName;
    private final List<String> originalCountryName = new ArrayList<>();

    public MapEditor(MapKind kind, long seed) {
        this.kind = kind;
        this.seed = seed;
        this.game = Game.create(kind, seed, null);
        int n = game.world.provinces.size();
        originalOwner = new int[n];
        originalPopulation = new int[n];
        originalProvinceName = new String[n];
        for (Province p : game.world.provinces) {
            originalOwner[p.id] = p.owner == null ? -1 : p.owner.id;
            originalPopulation[p.id] = p.population;
            originalProvinceName[p.id] = p.name;
        }
        for (Country c : game.world.countries) originalCountryName.add(c.name);
    }

    // ---------------------------------------------------------------- editing

    /** Names must be unique among living nations and may not contain the characters of the scenario syntax. */
    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.indexOf('=') < 0 && name.indexOf(',') < 0 && name.indexOf('\n') < 0
                && name.indexOf('\r') < 0 && name.equals(name.strip());
    }

    private boolean nameTaken(String name, Country except) {
        for (Country c : game.world.countries) if (c.alive && c != except && c.name.equals(name)) return true;
        return false;
    }

    public Country newCountry(String name, int rgb) {
        if (!validName(name) || nameTaken(name, null)) return null;
        Country c = new Country(game.world.countries.size(), name, rgb & 0xFFFFFF);
        game.world.countries.add(c);
        return c;
    }

    public boolean rename(Country c, String name) {
        if (!validName(name) || nameTaken(name, c)) return false;
        c.name = name;
        return true;
    }

    /** Gives a land province to a nation. */
    public boolean assign(Province p, Country c) {
        if (p == null || c == null || !p.isLand() || !c.alive || p.owner == c) return false;
        game.world.setOwner(p, c);
        // Provinces change hands without armies: the armies of the old owner stay where they are, which is harmless in the editor.
        fixNations();
        game.mapDirty = true;
        return true;
    }

    public void setName(Province p, String name) {
        if (p != null && name != null && !name.isBlank() && name.indexOf('\n') < 0) p.name = name.strip();
    }

    public void setPopulation(Province p, int population) {
        if (p != null && p.isLand()) p.population = Math.max(0, population);
    }

    private void fixNations() {
        for (Country c : game.world.countries) {
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

    public List<Country> nations() {
        List<Country> out = new ArrayList<>();
        for (Country c : game.world.countries) if (c.alive) out.add(c);
        return out;
    }

    // ---------------------------------------------------------------- export

    /** The scenario text describing everything that differs from the generated world. */
    public String export(String displayName, int startYear) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Made with the map editor\n");
        sb.append("name ").append(displayName).append('\n');
        sb.append("start ").append(startYear).append('\n');
        sb.append("maps ").append(kind.name().toLowerCase()).append('\n');
        if (kind == MapKind.RANDOM) sb.append("seed ").append(seed).append('\n');
        // Renames first: new nations may reuse a name that an old nation has given up.
        for (int i = 0; i < originalCountryName.size(); i++) {
            Country c = game.world.countries.get(i);
            if (!c.name.equals(originalCountryName.get(i))) sb.append("rename ").append(originalCountryName.get(i)).append(" = ").append(c.name).append('\n');
        }
        for (int i = originalCountryName.size(); i < game.world.countries.size(); i++) {
            Country c = game.world.countries.get(i);
            if (c.alive) sb.append("newcountry ").append(c.name).append(' ').append(String.format("%06X", c.rgb)).append('\n');
        }
        Set<Integer> seen = new HashSet<>();
        for (Province p : game.world.provinces) {
            if (!p.isLand() || p.owner == null) continue;
            if (p.owner.id != originalOwner[p.id] && seen.add(p.id)) sb.append("owner ").append(p.id).append(' ').append(p.owner.name).append('\n');
        }
        for (Province p : game.world.provinces) {
            if (!p.isLand()) continue;
            if (!p.name.equals(originalProvinceName[p.id])) sb.append("provname ").append(p.id).append(' ').append(p.name).append('\n');
            if (p.population != originalPopulation[p.id]) sb.append("population ").append(p.id).append(' ').append(p.population).append('\n');
        }
        return sb.toString();
    }

    /** Writes the scenario to {@code mods/scenarios/<id>.txt} and returns the file. */
    public Path save(String id, String displayName, int startYear) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) throw new IOException("The file name may only contain letters, digits, - and _");
        Files.createDirectories(Scenario.MOD_DIR);
        Path file = Scenario.MOD_DIR.resolve(id + ".txt");
        Files.writeString(file, export(displayName, startYear), StandardCharsets.UTF_8);
        return file;
    }
}
