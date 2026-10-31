package mapgame.tools;

import java.io.IOException;
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

/**
 * Headless unit tests for the simulation rules (pathfinding, combat, occupation, peace deals, alliances,
 * supply, reinforcement, front planning, saving). No test framework needed: run after build.ps1 with
 * {@code java -cp out\classes mapgame.tools.SelfTest}; the exit code is 1 when a check fails.
 */
public final class SelfTest {
    private static int checks, failures;

    private interface Test {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        run("pathfinding", SelfTest::pathfinding);
        run("combat", SelfTest::combat);
        run("occupation", SelfTest::occupation);
        run("peace deal limits", SelfTest::peaceLimits);
        run("alliances", SelfTest::alliances);
        run("supply and attrition", SelfTest::supply);
        run("group reinforcement", SelfTest::reinforcement);
        run("front planner", SelfTest::frontPlanner);
        run("army types and experience", SelfTest::armyTypes);
        run("fog of war", SelfTest::fogOfWar);
        run("save and load", SelfTest::saveLoad);
        System.out.printf("%d checks, %d failed%n", checks, failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(String name, Test t) {
        int before = failures;
        try {
            t.run();
        } catch (Exception | AssertionError e) {
            failures++;
            System.out.println("  ERROR in " + name + ": " + e);
        }
        System.out.println((failures == before ? "ok     " : "FAILED ") + name);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  check failed: " + what);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Game newGame() {
        Game g = Game.create(MapKind.RANDOM, 42);
        g.logSink = s -> { };
        return g;
    }

    /** Two touching land provinces of different nations: {a's province, b's province}. */
    private static Province[] border(Game g) {
        for (Province p : g.world.provinces) {
            if (!p.isLand() || p.owner == null) continue;
            for (Province q : p.neighbors) {
                if (q.isLand() && q.owner != null && q.owner != p.owner && !q.ferryTo.contains(p)) return new Province[] {p, q};
            }
        }
        throw new AssertionError("no border found");
    }

    private static Army army(Game g, Country owner, Province where, int men) {
        Army a = new Army(900_000 + g.armies.size(), owner, where, men);
        g.armies.add(a);
        return a;
    }

    // ---------------------------------------------------------------- tests

    private static void pathfinding() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.declareWar(a, o);
        List<Province> path = g.findPath(a, b[0], p -> p == b[1]);
        check(path != null && !path.isEmpty(), "path to the neighbouring enemy province exists");
        if (path == null) return;
        check(path.get(path.size() - 1) == b[1], "path ends at the goal");
        Province prev = b[0];
        boolean connected = true;
        for (Province p : path) {
            connected &= prev.neighbors.contains(p);
            prev = p;
        }
        check(connected, "every step moves to a neighbouring province");
        check(g.findPath(a, b[0], p -> p == b[0]) == null, "the start itself is not a goal");
    }

    private static void combat() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        g.armies.clear();
        g.declareWar(a, o);
        Army big = army(g, a, b[0], 3000), small = army(g, o, b[0], 1000);
        for (int i = 0; i < 3; i++) g.tickDay();
        check(small.dead || small.strength < 1000, "the weaker army loses men");
        check(big.strength < 3000, "the stronger army takes some losses too");
        check(small.dead || big.strength > small.strength, "the stronger army wins");
    }

    private static void occupation() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        g.armies.clear();
        g.declareWar(a, o);
        army(g, a, b[1], 1500);
        for (int i = 0; i < Game.OCCUPY_DAYS; i++) g.tickDay();
        check(b[1].controller == a, "an army that stays long enough occupies the province");
        check(b[1].owner == o, "occupation does not change the owner");
    }

    private static void peaceLimits() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.declareWar(a, o);
        int before = o.provinces.size();
        for (Province p : new ArrayList<>(o.provinces)) p.controller = a;
        g.makePeace(a, o);
        int lost = before - o.provinces.size();
        check(lost <= (int) Math.ceil(before * Game.PEACE_SHARE), "the winner takes at most " + (int) (Game.PEACE_SHARE * 100) + "% of the land");
        check(o.provinces.size() >= 1 && o.alive, "the loser keeps at least one province in a normal war");
        check(!g.atWar(a, o), "the war is over");
        Province gained = null;
        for (Province p : a.provinces) if (p.conqueredDay == g.day) gained = p;
        check(lost == 0 || gained != null, "conquered provinces are marked for the integration penalty");
        if (gained != null) check(gained.integration(g.day) < 1.0, "conquered provinces yield less at first");
    }

    private static void alliances() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        if (a.provinces.size() < o.provinces.size()) {
            Country t = a;
            a = o;
            o = t;
        }
        g.player = a;
        for (Army x : g.armiesOf(a)) x.strength = 100_000;
        String reply = g.proposeAlliance(a, o);
        check(g.allied(a, o), "a much stronger proposer gets the alliance: " + reply);
        check(!g.declareWar(a, o), "allies cannot declare war on each other");
        Province theirs = o.provinces.get(0);
        check(g.allowed(a, theirs), "allied armies may enter each other's land");

        Country third = null;
        for (Country c : g.world.countries) if (c != a && c != o && c.alive) third = c;
        int events = g.events.size();
        g.declareWar(third, o);
        check(g.events.size() > events, "the player is notified when an ally is attacked");
        check(!g.atWar(a, o), "attacking an ally is impossible");

        g.breakAlliance(a, o);
        check(!g.allied(a, o), "an alliance can be broken");
    }

    private static void supply() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        Army home = new Army(1, a, b[0], 2000);
        check(g.supplyDepth(home) == 0 && g.attritionRate(home) == 0, "no attrition on own land");
        Army edge = new Army(2, a, b[1], 2000);
        check(g.supplyDepth(edge) == 1 && g.attritionRate(edge) == 0, "no attrition right behind the own border");
        Army far = null;
        for (Province p : o.provinces) {
            Army test = new Army(3, a, p, 2000);
            if (g.supplyDepth(test) >= 3) {
                far = test;
                break;
            }
        }
        if (far == null) {
            check(true, "(no deep enemy province on this map, skipped)");
            return;
        }
        check(g.attritionRate(far) > 0, "armies far from their own land lose men");
        Army huge = new Army(4, a, far.location, 12_000);
        check(g.attritionRate(huge) > g.attritionRate(far), "big stacks lose more");
    }

    private static void reinforcement() {
        Game g = newGame();
        Country me = g.world.countries.get(0);
        g.player = me;
        ArmyGroup grp = g.createGroup(me);
        for (Army a : g.armiesOf(me)) g.assignToGroup(grp, a);
        grp.reinforce = true;
        grp.goal = grp.strength() + 2000;
        for (Army a : grp.armies) a.strength /= 2;
        int hurt = grp.strength();
        for (int i = 0; i < 90; i++) g.tickDay();
        check(grp.strength() > hurt, "a reinforcing group regains strength");
        check(grp.strength() >= grp.goal - Game.RECRUIT_MEN, "the group gets within one recruit of its target strength (" + grp.strength() + " of " + grp.goal
                + ", gold " + (int) me.gold + ", manpower " + me.manpower + ")");
    }

    private static void frontPlanner() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        g.declareWar(a, o);
        ArmyGroup grp = g.createGroup(a);
        for (Army x : g.armiesOf(a)) g.assignToGroup(grp, x);
        grp.target = o;
        grp.stance = ArmyGroup.Stance.ATTACK;
        g.planGroup(grp);
        check(!grp.front.isEmpty(), "an attacking group finds a front");
        boolean enemyOnly = true;
        for (Province p : grp.front) enemyOnly &= p.owner == o;
        check(enemyOnly, "the attack front consists of enemy provinces");
        boolean moving = false;
        for (Army x : grp.armies) moving |= !x.path.isEmpty() || grp.slots.containsKey(x);
        check(moving, "the group's armies get orders");
    }

    private static void fogOfWar() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner;
        boolean[] vis = g.visibleTo(a);
        boolean ownVisible = true;
        for (Province p : a.provinces) ownVisible &= vis[p.id];
        check(ownVisible, "all own provinces are visible");
        check(vis[b[1].id], "provinces next to own land are visible");
        int hidden = 0;
        Province far = null;
        for (boolean v : vis) if (!v) hidden++;
        for (Province p : g.world.provinces) if (!vis[p.id] && p.isLand()) far = p;
        check(hidden > 0 && far != null, "distant provinces stay hidden");
        if (far == null) return;
        g.armies.add(new Army(900_001, a, far, 1000));
        check(g.visibleTo(a)[far.id], "an own army reveals its province");
    }

    private static void armyTypes() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        g.armies.clear();
        g.declareWar(a, o);
        Army inf = army(g, a, b[0], 1000), cav = army(g, a, b[0], 1000);
        cav.type = mapgame.model.ArmyType.CAVALRY;
        inf.setPath(List.of(b[1]));
        cav.setPath(List.of(b[1]));
        g.tickDay();
        check(cav.progress > inf.progress, "cavalry marches faster than infantry");

        g.armies.clear();
        Army x = army(g, a, b[0], 1000), y = army(g, a, b[0], 1000), gun = army(g, a, b[0], 1000);
        gun.type = mapgame.model.ArmyType.ARTILLERY;
        x.experience = 40;
        check(g.mergeCandidates(x) == 1, "only armies of the same type can merge");
        g.mergeStack(x);
        check(x.strength == 2000 && x.experience == 20, "merging adds the men and averages the experience");
        check(!gun.dead, "artillery stays separate");

        g.armies.clear();
        Army defender = army(g, o, b[0], 1500), shooter = army(g, a, b[0], 1500);
        g.declareWar(a, o);
        for (int i = 0; i < 3; i++) g.tickDay();
        check(defender.experience > 0 && shooter.experience > 0, "fighting armies gain experience");

        Army vet = new Army(5, a, b[0], 1000), green = new Army(6, a, b[0], 1000);
        vet.experience = 80;
        check(vet.attackBonus() > green.attackBonus() && vet.toughness() < green.toughness(), "veterans hit harder and take less damage");
        vet.addMen(1000);
        check(vet.experience == 40, "new men water down experience");
    }

    private static void saveLoad() throws IOException {
        Game g = newGame();
        Province[] b = border(g);
        g.player = b[0].owner;
        g.declareWar(b[0].owner, b[1].owner);
        for (int i = 0; i < 200; i++) g.tickDay();
        Path file = Files.createTempFile("selftest", SaveFile.EXTENSION);
        try {
            SaveFile.save(g, file);
            Game h = SaveFile.load(file);
            check(h.day == g.day, "the day is restored");
            check(h.player == null ? g.player == null : h.player.id == g.player.id, "the player nation is restored");
            check(h.armies.size() == g.armies.stream().filter(x -> !x.dead).count(), "all armies are restored");
            boolean same = true;
            for (Country c : g.world.countries) {
                Country d = h.world.countries.get(c.id);
                same &= d.alive == c.alive && d.provinces.size() == c.provinces.size() && d.manpower == c.manpower;
            }
            check(same, "countries are restored");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
