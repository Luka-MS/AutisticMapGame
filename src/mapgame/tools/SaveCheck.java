package mapgame.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.sim.Game;
import mapgame.sim.SaveFile;

/** Round-trip test for saving: save, load, then let both games run on and compare them. */
public final class SaveCheck {
    public static void main(String[] args) throws Exception {
        boolean ok = true;
        for (MapKind kind : MapKind.values()) ok &= check(kind, 42);
        ok &= checkBrokenFiles();
        System.out.println(ok ? "ALL CHECKS PASSED" : "CHECKS FAILED");
        System.exit(ok ? 0 : 1);
    }

    private static boolean check(MapKind kind, long seed) throws Exception {
        Game g = Game.create(kind, seed);
        g.logSink = s -> { };
        Country me = g.world.countries.stream().max((a, b) -> a.provinces.size() - b.provinces.size()).get();
        g.player = me;
        me.gold = 5000;
        me.manpower = 100000;
        for (int i = 0; i < 6; i++) g.recruit(me, me.capital);
        Country foe = null;
        for (Province p : me.provinces) {
            for (Province q : p.neighbors) if (q.owner != null && q.owner != me) foe = q.owner;
        }
        ArmyGroup grp = g.createGroup(me);
        for (Army a : g.armiesOf(me)) g.assignToGroup(grp, a);
        if (foe != null) {
            grp.target = foe;
            g.declareWar(me, foe, true);
        }
        for (int d = 0; d < 400; d++) g.tickDay();

        Path file = Files.createTempFile("mapgame-test", SaveFile.EXTENSION);
        SaveFile.save(g, file);
        Game loaded = SaveFile.load(file);
        loaded.logSink = s -> { };
        long size = Files.size(file);
        Files.delete(file);

        boolean same = fingerprint(g).equals(fingerprint(loaded));
        for (int d = 0; d < 300; d++) {
            g.tickDay();
            loaded.tickDay();
        }
        boolean sameLater = fingerprint(g).equals(fingerprint(loaded));
        System.out.printf("%-7s day %d: save %,d bytes, identical after load: %s, identical 300 days later: %s (alive %d/%d)%n",
                kind, loaded.day - 300, size, same, sameLater, g.aliveCountries().size(), loaded.aliveCountries().size());
        return same && sameLater;
    }

    private static boolean checkBrokenFiles() throws Exception {
        Path junk = Files.createTempFile("mapgame-junk", SaveFile.EXTENSION);
        Files.writeString(junk, "this is not a save file");
        boolean rejectedJunk = rejects(junk);

        Game g = Game.create(MapKind.RANDOM, 7);
        Path good = Files.createTempFile("mapgame-good", SaveFile.EXTENSION);
        SaveFile.save(g, good);
        byte[] bytes = Files.readAllBytes(good);
        Path cut = Files.createTempFile("mapgame-cut", SaveFile.EXTENSION);
        Files.write(cut, java.util.Arrays.copyOf(bytes, bytes.length / 2));
        boolean rejectedCut = rejects(cut);
        for (Path p : List.of(junk, good, cut)) Files.deleteIfExists(p);
        System.out.println("Broken files rejected cleanly: junk=" + rejectedJunk + ", truncated=" + rejectedCut);
        return rejectedJunk && rejectedCut;
    }

    private static boolean rejects(Path p) {
        try {
            SaveFile.load(p);
            return false;
        } catch (java.io.IOException e) {
            return true;
        }
    }

    private static String fingerprint(Game g) {
        StringBuilder sb = new StringBuilder();
        sb.append(g.day).append('|').append(g.player == null ? -1 : g.player.id).append('|').append(g.rnd.nextLong()).append('\n');
        for (Province p : g.world.provinces) {
            sb.append(p.owner == null ? -1 : p.owner.id).append(',').append(p.controller == null ? -1 : p.controller.id).append(';');
        }
        for (Country c : g.world.countries) {
            sb.append('\n').append(c.alive).append(',').append(c.gold).append(',').append(c.manpower).append(',').append(c.truceUntil);
            for (Province p : c.provinces) sb.append(',').append(p.id);
        }
        for (Army a : g.armies) {
            if (a.dead) continue;
            sb.append('\n').append(a.id).append(',').append(a.owner.id).append(',').append(a.location.id).append(',')
                    .append(a.strength).append(',').append(a.progress).append(',').append(a.daysHere).append(',')
                    .append(a.group == null ? 0 : a.group.id);
            for (Province p : a.path) sb.append(',').append(p.id);
        }
        for (ArmyGroup grp : g.groups) {
            sb.append('\n').append(grp.id).append(',').append(grp.name).append(',').append(grp.target == null ? -1 : grp.target.id)
                    .append(',').append(grp.stance).append(',').append(grp.armies.size()).append(',').append(grp.front.size());
        }
        List<String> wars = new ArrayList<>();
        for (Country a : g.world.countries) {
            for (Country b : g.world.countries) {
                if (a.id < b.id && g.atWar(a, b)) wars.add(a.id + "-" + b.id + (g.isTotalWar(a, b) ? "T" : "") + "@" + g.warStart(a, b));
            }
        }
        sb.append('\n').append(wars);
        return sb.toString();
    }
}
