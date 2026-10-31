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
import mapgame.model.ArmyType;
import mapgame.model.Building;
import mapgame.model.Country;
import mapgame.model.Fleet;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.model.Policy;
import mapgame.model.TechField;

/**
 * Saves and loads a game. Only the changing state is stored; the map itself is recreated from
 * map type + seed (generation is deterministic) and verified with a fingerprint.
 */
public final class SaveFile {
    public static final String EXTENSION = ".sav";
    private static final int MAGIC = 0x4D47534D;
    /** Format version; each feature added fields (2 conquest day ... 16 fleets, 17 hotseat players). Older versions load too. */
    private static final int VERSION = 17;

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
        out.writeUTF(g.scenarioId == null ? "" : g.scenarioId);
        out.writeInt(Arrays.hashCode(g.world.ids));
        out.writeInt(g.world.provinces.size());
        out.writeInt(g.world.countries.size());

        out.writeInt(g.day);
        out.writeInt(g.player == null ? -1 : g.player.id);
        out.writeInt(g.humans.size());
        for (Country h : g.humans) out.writeInt(h.id);
        long rngSeed = g.rnd.nextLong();
        g.rnd.setSeed(rngSeed);
        out.writeLong(rngSeed);
        out.writeInt(g.nextArmyId);
        out.writeInt(g.nextGroupId);
        out.writeBoolean(g.fogOfWar);
        out.writeInt(g.difficulty.ordinal());
        out.writeInt(g.aggression);

        for (Country c : g.world.countries) {
            out.writeBoolean(c.alive);
            out.writeDouble(c.gold);
            out.writeInt(c.manpower);
            out.writeInt(c.truceUntil);
            for (int lvl : c.tech) out.writeByte(lvl);
            out.writeInt(c.researching == null ? -1 : c.researching.ordinal());
            out.writeDouble(c.researchProgress);
            out.writeInt(c.taxes.ordinal());
            out.writeInt(c.conscription.ordinal());
            out.writeInt(c.policyChangedDay);
            out.writeDouble(c.incomeBonus);
            out.writeInt(c.incomeBonusUntil);
            out.writeInt(c.capital == null ? -1 : c.capital.id);
            out.writeInt(c.provinces.size());
            for (Province p : c.provinces) out.writeInt(p.id);
        }
        for (Province p : g.world.provinces) {
            out.writeInt(p.owner == null ? -1 : p.owner.id);
            out.writeInt(p.controller == null ? -1 : p.controller.id);
            out.writeInt(p.conqueredDay);
            out.writeInt(p.buildings.length);
            for (int lvl : p.buildings) out.writeByte(lvl);
            out.writeInt(p.building == null ? -1 : p.building.ordinal());
            out.writeInt(p.buildDone);
            out.writeDouble(p.unrest);
            out.writeInt(p.population);
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
        out.writeInt(g.pacts.size());
        for (Map.Entry<Long, Integer> e : g.pacts.entrySet()) {
            out.writeLong(e.getKey());
            out.writeInt(e.getValue());
        }
        out.writeInt(g.guarantees.size());
        for (long k : g.guarantees) out.writeLong(k);
        out.writeInt(g.trades.size());
        for (long k : g.trades) out.writeLong(k);
        out.writeInt(g.warGoals.size());
        for (Map.Entry<Long, Integer> e : g.warGoals.entrySet()) {
            out.writeLong(e.getKey());
            out.writeInt(e.getValue());
        }

        List<Army> alive = g.armies.stream().filter(a -> !a.dead).toList();
        out.writeInt(alive.size());
        for (Army a : alive) {
            out.writeInt(a.id);
            out.writeInt(a.owner.id);
            out.writeInt(a.location.id);
            out.writeInt(a.strength);
            out.writeDouble(a.progress);
            out.writeInt(a.daysHere);
            out.writeInt(a.type.ordinal());
            out.writeDouble(a.experience);
            out.writeDouble(a.morale);
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

        List<Fleet> fleets = g.fleets.stream().filter(f -> !f.dead).toList();
        out.writeInt(g.nextFleetId);
        out.writeInt(fleets.size());
        for (Fleet f : fleets) {
            out.writeInt(f.id);
            out.writeInt(f.owner.id);
            out.writeInt(f.location.id);
            out.writeInt(f.strength);
            out.writeDouble(f.progress);
            out.writeInt(f.daysHere);
            out.writeInt(f.path.size());
            for (Province p : f.path) out.writeInt(p.id);
            out.writeInt(f.target == null ? -1 : f.target.id);
            List<Army> cargo = f.cargo.stream().filter(a -> !a.dead).toList();
            out.writeInt(cargo.size());
            for (Army a : cargo) out.writeInt(a.id);
        }
    }

    // ---------------------------------------------------------------- reading

    private static Game read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException(I18n.tr("save.invalid"));
        int version = in.readInt();
        if (version < 1 || version > VERSION) throw new IOException(I18n.tr("save.version", version));
        MapKind kind = MapKind.valueOf(in.readUTF());
        long seed = in.readLong();
        String scenarioId = version >= 15 ? in.readUTF() : "";
        int fingerprint = in.readInt();
        int provinceCount = in.readInt(), countryCount = in.readInt();

        Scenario scenario = scenarioId.isEmpty() ? null : Scenario.find(scenarioId);
        if (!scenarioId.isEmpty() && scenario == null) throw new IOException(I18n.tr("save.scenario", scenarioId));
        Game g = Game.create(kind, seed, scenario);
        if (Arrays.hashCode(g.world.ids) != fingerprint || g.world.provinces.size() != provinceCount
                || g.world.countries.size() != countryCount) {
            throw new IOException(I18n.tr("save.map"));
        }

        g.day = in.readInt();
        int playerId = in.readInt();
        g.player = playerId < 0 ? null : g.world.countries.get(playerId);
        g.humans.clear();
        if (version >= 17) {
            int humans = in.readInt();
            for (int i = 0; i < humans; i++) g.humans.add(g.world.countries.get(in.readInt()));
        }
        g.rnd.setSeed(in.readLong());
        g.nextArmyId = in.readInt();
        g.nextGroupId = in.readInt();
        if (version >= 7) g.fogOfWar = in.readBoolean();
        if (version >= 14) {
            g.difficulty = Difficulty.values()[in.readInt()];
            g.aggression = in.readInt();
        }

        for (Country c : g.world.countries) {
            c.alive = in.readBoolean();
            c.gold = in.readDouble();
            c.manpower = in.readInt();
            c.truceUntil = in.readInt();
            if (version >= 9) {
                for (int k = 0; k < c.tech.length; k++) c.tech[k] = in.readByte();
                int field = in.readInt();
                c.researching = field < 0 ? null : TechField.values()[field];
                c.researchProgress = in.readDouble();
            }
            if (version >= 10) {
                c.taxes = Policy.Taxes.values()[in.readInt()];
                c.conscription = Policy.Conscription.values()[in.readInt()];
                c.policyChangedDay = in.readInt();
            }
            if (version >= 11) {
                c.incomeBonus = in.readDouble();
                c.incomeBonusUntil = in.readInt();
            }
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
            if (version >= 8) {
                int kinds = in.readInt();
                for (int k = 0; k < kinds; k++) {
                    int lvl = in.readByte();
                    if (k < p.buildings.length) p.buildings[k] = lvl;
                }
                int job = in.readInt();
                p.building = job < 0 ? null : Building.values()[job];
                p.buildDone = in.readInt();
                p.unrest = in.readDouble();
            }
            if (version >= 11) {
                p.population = in.readInt();
            }
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
        g.pacts.clear();
        g.guarantees.clear();
        g.trades.clear();
        g.warGoals.clear();
        if (version >= 13) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) g.pacts.put(in.readLong(), in.readInt());
            n = in.readInt();
            for (int i = 0; i < n; i++) g.guarantees.add(in.readLong());
            n = in.readInt();
            for (int i = 0; i < n; i++) g.trades.add(in.readLong());
            n = in.readInt();
            for (int i = 0; i < n; i++) g.warGoals.put(in.readLong(), in.readInt());
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
            if (version >= 6) {
                a.type = ArmyType.values()[in.readInt()];
                a.experience = in.readDouble();
            }
            if (version >= 12) {
                a.morale = in.readDouble();
            }
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

        g.fleets.clear();
        if (version >= 16) {
            g.nextFleetId = in.readInt();
            int fleets = in.readInt();
            for (int i = 0; i < fleets; i++) {
                int id = in.readInt();
                Country owner = g.world.countries.get(in.readInt());
                Province location = g.world.provinces.get(in.readInt());
                Fleet f = new Fleet(id, owner, location, in.readInt());
                f.progress = in.readDouble();
                f.daysHere = in.readInt();
                int steps = in.readInt();
                for (int k = 0; k < steps; k++) f.path.add(g.world.provinces.get(in.readInt()));
                int target = in.readInt();
                f.target = target < 0 ? null : g.world.provinces.get(target);
                int cargo = in.readInt();
                for (int k = 0; k < cargo; k++) {
                    Army a = byId.get(in.readInt());
                    if (a == null) continue;
                    a.fleet = f;
                    f.cargo.add(a);
                }
                g.fleets.add(f);
            }
        }

        g.world.recomputeLabels();
        g.diplomacyChanged();
        g.mapDirty = true;
        return g;
    }
}
