package mapgame.sim;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import mapgame.i18n.I18n;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;

/**
 * Saves and loads a game. Only the changing state is stored; the map itself is recreated from
 * map type + seed (generation is deterministic) and verified with a fingerprint.
 */
public final class SaveFile {
    public static final String EXTENSION = ".sav";
    private static final int MAGIC = 0x4D47534D;
    /** 2: conquest day. 3: capitals. 4: army group reinforcement. 5: alliances. Older versions can still be read. */
    private static final int VERSION = 5;

    private SaveFile() {
    }

    public static void save(Game g, Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
            write(g, out);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Game load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            return read(in);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(I18n.tr("save.damaged"), e);
        }
    }

    // ---------------------------------------------------------------- writing

    private static void write(Game g, DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeUTF(g.kind.name());
        out.writeLong(g.seed);
        out.writeInt(Arrays.hashCode(g.world.ids));
        out.writeInt(g.world.provinces.size());
        out.writeInt(g.world.countries.size());

        out.writeInt(g.day);
        out.writeInt(g.player == null ? -1 : g.player.id);
        long rngSeed = g.rnd.nextLong();
        g.rnd.setSeed(rngSeed);
        out.writeLong(rngSeed);
        out.writeInt(g.nextArmyId);
        out.writeInt(g.nextGroupId);

        for (Country c : g.world.countries) {
            out.writeBoolean(c.alive);
            out.writeDouble(c.gold);
            out.writeInt(c.manpower);
            out.writeInt(c.truceUntil);
            out.writeInt(c.capital == null ? -1 : c.capital.id);
            out.writeInt(c.provinces.size());
            for (Province p : c.provinces) out.writeInt(p.id);
        }
        for (Province p : g.world.provinces) {
            out.writeInt(p.owner == null ? -1 : p.owner.id);
            out.writeInt(p.controller == null ? -1 : p.controller.id);
            out.writeInt(p.conqueredDay);
        }

        out.writeInt(g.wars.size());
        for (Map.Entry<Long, Integer> e : g.wars.entrySet()) {
            out.writeLong(e.getKey());
            out.writeInt(e.getValue());
        }
        out.writeInt(g.totalWars.size());
        for (long k : g.totalWars) out.writeLong(k);
        out.writeInt(g.alliances.size());
        for (long k : g.alliances) out.writeLong(k);

        List<Army> alive = g.armies.stream().filter(a -> !a.dead).toList();
        out.writeInt(alive.size());
        for (Army a : alive) {
            out.writeInt(a.id);
            out.writeInt(a.owner.id);
            out.writeInt(a.location.id);
            out.writeInt(a.strength);
            out.writeDouble(a.progress);
            out.writeInt(a.daysHere);
            out.writeInt(a.path.size());
            for (Province p : a.path) out.writeInt(p.id);
        }

        out.writeInt(g.groups.size());
        for (ArmyGroup grp : g.groups) {
            out.writeInt(grp.id);
            out.writeInt(grp.owner.id);
            out.writeUTF(grp.name == null ? "" : grp.name);
            out.writeInt(grp.target == null ? -1 : grp.target.id);
            out.writeInt(grp.stance.ordinal());
            out.writeUTF(grp.status);
            out.writeBoolean(grp.reinforce);
            out.writeInt(grp.goal);
            List<Army> members = grp.armies.stream().filter(a -> !a.dead).toList();
            out.writeInt(members.size());
            for (Army a : members) {
                out.writeInt(a.id);
                Province slot = grp.slots.get(a);
                out.writeInt(slot == null ? -1 : slot.id);
            }
            out.writeInt(grp.front.size());
            for (Province p : grp.front) out.writeInt(p.id);
        }
    }

    // ---------------------------------------------------------------- reading

    private static Game read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException(I18n.tr("save.invalid"));
        int version = in.readInt();
        if (version < 1 || version > VERSION) throw new IOException(I18n.tr("save.version", version));
        MapKind kind = MapKind.valueOf(in.readUTF());
        long seed = in.readLong();
        int fingerprint = in.readInt();
        int provinceCount = in.readInt(), countryCount = in.readInt();

        Game g = Game.create(kind, seed);
        if (Arrays.hashCode(g.world.ids) != fingerprint || g.world.provinces.size() != provinceCount
                || g.world.countries.size() != countryCount) {
            throw new IOException(I18n.tr("save.map"));
        }

        g.day = in.readInt();
        int playerId = in.readInt();
        g.player = playerId < 0 ? null : g.world.countries.get(playerId);
        g.rnd.setSeed(in.readLong());
        g.nextArmyId = in.readInt();
        g.nextGroupId = in.readInt();

        for (Country c : g.world.countries) {
            c.alive = in.readBoolean();
            c.gold = in.readDouble();
            c.manpower = in.readInt();
            c.truceUntil = in.readInt();
            if (version >= 3) {
                int capital = in.readInt();
                if (capital >= 0) c.capital = g.world.provinces.get(capital);
            }
            c.provinces.clear();
            int n = in.readInt();
            for (int i = 0; i < n; i++) c.provinces.add(g.world.provinces.get(in.readInt()));
        }
        for (Province p : g.world.provinces) {
            int owner = in.readInt(), controller = in.readInt();
            p.owner = owner < 0 ? null : g.world.countries.get(owner);
            p.controller = controller < 0 ? null : g.world.countries.get(controller);
            if (version >= 2) p.conqueredDay = in.readInt();
        }

        g.wars.clear();
        int wars = in.readInt();
        for (int i = 0; i < wars; i++) g.wars.put(in.readLong(), in.readInt());
        g.totalWars.clear();
        int totals = in.readInt();
        for (int i = 0; i < totals; i++) g.totalWars.add(in.readLong());
        g.alliances.clear();
        if (version >= 5) {
            int pacts = in.readInt();
            for (int i = 0; i < pacts; i++) g.alliances.add(in.readLong());
        }

        g.armies.clear();
        Map<Integer, Army> byId = new HashMap<>();
        int armies = in.readInt();
        for (int i = 0; i < armies; i++) {
            int id = in.readInt();
            Country owner = g.world.countries.get(in.readInt());
            Province location = g.world.provinces.get(in.readInt());
            Army a = new Army(id, owner, location, in.readInt());
            a.progress = in.readDouble();
            a.daysHere = in.readInt();
            int pathLength = in.readInt();
            for (int k = 0; k < pathLength; k++) a.path.add(g.world.provinces.get(in.readInt()));
            g.armies.add(a);
            byId.put(id, a);
        }

        g.groups.clear();
        int groups = in.readInt();
        for (int i = 0; i < groups; i++) {
            int id = in.readInt();
            ArmyGroup grp = new ArmyGroup(id, g.world.countries.get(in.readInt()));
            String name = in.readUTF();
            // Default names follow the language setting instead of being frozen in the save.
            grp.name = name.isEmpty() || name.matches("Army Group \\d+") ? null : name;
            int target = in.readInt();
            grp.target = target < 0 ? null : g.world.countries.get(target);
            grp.stance = ArmyGroup.Stance.values()[in.readInt()];
            grp.status = in.readUTF();
            if (version >= 4) {
                grp.reinforce = in.readBoolean();
                grp.goal = in.readInt();
            }
            int members = in.readInt();
            for (int k = 0; k < members; k++) {
                Army a = byId.get(in.readInt());
                int slot = in.readInt();
                if (a == null) continue;
                a.group = grp;
                grp.armies.add(a);
                if (slot >= 0) grp.slots.put(a, g.world.provinces.get(slot));
            }
            int front = in.readInt();
            for (int k = 0; k < front; k++) grp.front.add(g.world.provinces.get(in.readInt()));
            g.groups.add(grp);
        }

        g.world.recomputeLabels();
        g.mapDirty = true;
        return g;
    }
}
