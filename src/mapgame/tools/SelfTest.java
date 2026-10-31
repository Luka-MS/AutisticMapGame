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
        run("treaties and war goals", SelfTest::treaties);
        run("supply and attrition", SelfTest::supply);
        run("group reinforcement", SelfTest::reinforcement);
        run("front planner", SelfTest::frontPlanner);
        run("army types and experience", SelfTest::armyTypes);
        run("morale and generals", SelfTest::moraleAndGenerals);
        run("fog of war", SelfTest::fogOfWar);
        run("buildings and unrest", SelfTest::buildingsAndUnrest);
        run("research", SelfTest::research);
        run("policies", SelfTest::policies);
        run("difficulty", SelfTest::difficulty);
        run("scenarios", SelfTest::scenarios);
        run("mods", SelfTest::mods);
        run("navy", SelfTest::navy);
        run("map editor", SelfTest::mapEditor);
        run("events and decisions", SelfTest::events);
        run("language files", SelfTest::languages);
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

    private static void buildingsAndUnrest() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        a.gold = 1000;
        Province home = b[0];
        double before = home.income(g.day);
        check(g.build(a, home, mapgame.model.Building.MARKET), "a province can be developed");
        check(a.gold == 1000 - mapgame.model.Building.MARKET.cost(0), "construction costs gold");
        check(!g.build(a, home, mapgame.model.Building.FORT), "only one construction at a time");
        for (int i = 0; i < mapgame.model.Building.BUILD_DAYS; i++) g.tickDay();
        check(home.level(mapgame.model.Building.MARKET) == 1 && home.building == null, "the building is finished after its build time");
        check(home.income(g.day) > before, "a market raises the income");

        // Occupied land without a garrison builds up unrest and finally rebels; a garrison keeps it quiet.
        Province held = b[1];
        Province held2 = null;
        for (Province p : o.provinces) if (p != held && p.controller == o) held2 = p;
        held.controller = a;
        if (held2 != null) {
            held2.controller = a;
            g.armies.add(new Army(900_100, a, held2, 1000));
        }
        for (int i = 0; i < 80; i++) g.tickDay();
        check(held.unrest > 20, "unrest grows in occupied land without a garrison");
        if (held2 != null) check(held2.unrest == 0, "a garrison keeps an occupied province quiet");
        for (int i = 0; i < 200; i++) g.tickDay();
        check(held.controller == o, "rebels free an occupied province without a garrison");
    }

    private static void research() {
        Game g = newGame();
        Country me = g.world.countries.get(0);
        g.player = me;
        check(g.startResearch(me, mapgame.model.TechField.ECONOMY), "research can be started");
        double before = g.monthlyIncome(me);
        int months = 0;
        while (me.techLevel(mapgame.model.TechField.ECONOMY) == 0 && months < 60) {
            for (int i = 0; i < 30; i++) g.tickDay();
            months++;
        }
        check(me.techLevel(mapgame.model.TechField.ECONOMY) == 1, "research finishes (" + months + " months)");
        check(g.monthlyIncome(me) > before * 1.05, "economy research raises the income");
        check(me.researching == null, "the player picks the next project himself");
        Army a = new Army(1, me, me.provinces.get(0), 1000);
        double base = a.attackBonus();
        me.tech[mapgame.model.TechField.MILITARY.ordinal()] = 3;
        check(a.attackBonus() > base && a.toughness() < 1, "military research strengthens armies");
        boolean aiResearches = false;
        for (Country c : g.world.countries) if (c != me && (c.researching != null || c.techLevel(mapgame.model.TechField.ECONOMY) > 0)) aiResearches = true;
        check(aiResearches, "AI nations research by themselves");
    }

    private static void policies() {
        Game g = newGame();
        Country me = g.world.countries.get(0);
        g.player = me;
        double income = g.monthlyIncome(me);
        int manpower = g.monthlyManpower(me);
        check(g.setPolicies(me, mapgame.model.Policy.Taxes.HIGH, mapgame.model.Policy.Conscription.NORMAL), "policies can be changed");
        check(g.monthlyIncome(me) > income && g.monthlyManpower(me) < manpower, "high taxes bring gold but cost manpower");
        check(!g.setPolicies(me, mapgame.model.Policy.Taxes.LOW, mapgame.model.Policy.Conscription.NORMAL), "policies cannot be changed again right away");
        for (int i = 0; i < mapgame.model.Policy.CHANGE_DELAY; i++) g.tickDay();
        check(g.setPolicies(me, mapgame.model.Policy.Taxes.LOW, mapgame.model.Policy.Conscription.HEAVY), "policies can be changed after the delay");
    }

    private static void events() {
        Game g = newGame();
        Country me = g.world.countries.get(0);
        g.player = me;
        int[] asked = {0};
        g.decisions = (title, text, options) -> {
            asked[0]++;
            check(!title.isEmpty() && !text.isEmpty() && options.size() >= 2, "decision texts exist: " + title);
            return asked[0] % 2;
        };
        for (int i = 0; i < 360 * 40; i++) g.tickDay();
        check(asked[0] > 0, "the player is asked about events (" + asked[0] + " decisions in 40 years)");
        check(g.events.stream().anyMatch(e -> e.text().contains(" - ")), "event results are reported");
    }

    private static void languages() throws IOException {
        java.util.Properties en = props("en");
        for (String code : new String[] {"de", "ru", "sh"}) {
            java.util.Properties p = props(code);
            java.util.List<String> missing = new ArrayList<>();
            for (String k : en.stringPropertyNames()) if (!p.containsKey(k)) missing.add(k);
            check(missing.isEmpty(), code + ".properties lacks keys: " + missing);
            java.util.List<String> extra = new ArrayList<>();
            for (String k : p.stringPropertyNames()) if (!en.containsKey(k)) extra.add(k);
            check(extra.isEmpty(), code + ".properties has unknown keys: " + extra);
        }
    }

    private static java.util.Properties props(String code) throws IOException {
        java.util.Properties p = new java.util.Properties();
        try (java.io.InputStream in = SelfTest.class.getResourceAsStream("/mapgame/lang/" + code + ".properties");
                java.io.Reader r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    private static void moraleAndGenerals() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        g.armies.clear();
        g.declareWar(a, o);
        Army weak = army(g, a, b[0], 1500), strong = army(g, o, b[0], 4000);
        g.tickDay();
        check(weak.morale < 100, "morale falls in battle");
        check(strong.morale > weak.morale, "the army taking heavier losses loses more morale");

        g.armies.clear();
        Army rest = army(g, a, b[0], 1000);
        rest.morale = 50;
        for (int i = 0; i < 10; i++) g.tickDay();
        check(rest.morale > 70, "resting armies recover morale (" + (int) rest.morale + ")");

        Army raw = new Army(1, a, b[0], 1000), vet = new Army(1, a, b[0], 1000);
        vet.experience = 60;
        check(raw.general() == null && vet.general() != null && vet.generalSkill() == 2, "veteran armies get a general");
        raw.morale = 20;
        check(raw.attackBonus() < vet.attackBonus(), "shaken and green armies hit weaker");
    }

    private static void treaties() {
        Game g = newGame();
        Province[] b = border(g);
        Country a = b[0].owner, o = b[1].owner;
        g.player = a;
        g.proposePact(a, o);
        check(g.hasPact(a, o), "a non-aggression pact is signed");
        check(!g.declareWar(a, o), "a pact prevents war");
        g.cancelPact(a, o);
        check(!g.hasPact(a, o) && g.declareWar(a, o), "after cancelling, war is possible");
        check(g.proposeTrade(a, o) != null && !g.hasTrade(a, o), "no trade during a war");
        g.makePeace(a, o);

        double income = g.monthlyIncome(a);
        g.proposeTrade(a, o);
        check(g.hasTrade(a, o) && g.monthlyIncome(a) > income, "a trade agreement raises the income");
        g.declareWar(a, o);
        check(!g.hasTrade(a, o), "war ends trade");

        // Tribute war: no land, gold changes hands.
        Game h = newGame();
        Province[] c = border(h);
        Country x = c[0].owner, y = c[1].owner;
        h.player = x;
        h.declareWar(x, y);
        check(h.warGoal(x, y) == Game.WarGoal.CONQUEST, "the default war goal is conquest");
        h.nextWarGoal = Game.WarGoal.TRIBUTE;
        h.makePeace(x, y);
        h.declareWar(x, y);
        check(h.warGoal(x, y) == Game.WarGoal.TRIBUTE, "the player's war goal is used");
        int landBefore = y.provinces.size();
        for (Province p : new ArrayList<>(y.provinces)) p.controller = x;
        double goldY = y.gold, goldX = x.gold;
        h.makePeace(x, y);
        check(y.provinces.size() == landBefore, "a tribute war takes no land");
        check(y.gold < goldY && x.gold > goldX, "the loser pays tribute");

        // A guarantor is notified when its protégé is attacked.
        Game k = newGame();
        Province[] d = border(k);
        Country gu = d[0].owner, prot = d[1].owner;
        k.player = gu;
        k.guarantee(gu, prot);
        Country attacker = null;
        for (Country z : k.world.countries) if (z != gu && z != prot && z.alive) attacker = z;
        int events = k.events.size();
        k.declareWar(attacker, prot);
        check(k.events.size() > events, "the guarantor is notified");
    }

    private static void difficulty() {
        Game g = newGame();
        Country me = g.world.countries.get(0), other = g.world.countries.get(1);
        g.player = me;
        double normalMe = g.monthlyIncome(me), normalAi = g.monthlyIncome(other);
        g.difficulty = mapgame.sim.Difficulty.HARD;
        check(g.monthlyIncome(me) < normalMe && g.monthlyIncome(other) > normalAi, "hard: the AI earns more, the player less");
        g.difficulty = mapgame.sim.Difficulty.EASY;
        check(g.monthlyIncome(me) > normalMe && g.monthlyIncome(other) < normalAi, "easy: the player earns more, the AI less");

        Game peaceful = newGame();
        peaceful.aggression = 0;
        for (int i = 0; i < 360 * 5; i++) peaceful.tickDay();
        check(peaceful.warsDeclared == 0, "with aggression 0 the AI starts no wars (" + peaceful.warsDeclared + ")");
        Game warlike = newGame();
        warlike.aggression = 100;
        for (int i = 0; i < 360 * 5; i++) warlike.tickDay();
        check(warlike.warsDeclared > 0, "with aggression 100 the AI starts wars");
    }

    private static void scenarios() throws IOException {
        java.util.List<mapgame.sim.Scenario> all = mapgame.sim.Scenario.all();
        check(all.stream().anyMatch(s -> s.id.equals("1914")) && all.stream().anyMatch(s -> s.id.equals("1936")), "the bundled scenarios are found");
        mapgame.sim.Scenario s1914 = mapgame.sim.Scenario.find("1914");
        check(s1914 != null && s1914.startYear == 1914, "the 1914 scenario starts in 1914");
        if (s1914 == null) return;
        Game g = Game.create(MapKind.EUROPE, 3, s1914);
        check(g.day == 14 * 360, "the calendar starts in the scenario year");
        Country empire = null;
        for (Country c : g.world.countries) if (c.alive && c.name.equals("Austria-Hungary")) empire = c;
        check(empire != null && empire.provinces.size() > 8, "Austria-Hungary is a merged empire");
        boolean hungaryGone = true;
        for (Country c : g.world.countries) if (c.alive && c.name.equals("Hungary")) hungaryGone = false;
        check(hungaryGone, "merged nations disappear");
        Country germany = null;
        for (Country c : g.world.countries) if (c.alive && c.name.equals("German Empire")) germany = c;
        check(germany != null && empire != null && g.allied(germany, empire), "the scenario sets up alliances");
        for (int i = 0; i < 200; i++) g.tickDay();
        Path file = Files.createTempFile("scenario", SaveFile.EXTENSION);
        try {
            SaveFile.save(g, file);
            Game h = SaveFile.load(file);
            check("1914".equals(h.scenarioId) && h.day == g.day, "a save remembers its scenario");
            check(h.world.countries.stream().filter(c -> c.alive).count() == g.world.countries.stream().filter(c -> c.alive).count(), "the loaded world has the same nations");
        } finally {
            Files.deleteIfExists(file);
        }
        // Scenario text: own nation and owner changes (what the map editor writes).
        mapgame.sim.Scenario custom = mapgame.sim.Scenario.parseForTest("t", "start 1920\nnewcountry Atlantis 8844AA\nowner 5 Atlantis\n");
        Game c = Game.create(MapKind.RANDOM, 42, custom);
        check(c.world.countries.stream().anyMatch(x -> x.name.equals("Atlantis") && x.alive) || !c.world.provinces.get(5).isLand(),
                "scenarios can add nations and change owners");
    }

    private static void mods() throws IOException {
        Path dir = Path.of("mods", "scenarios");
        boolean existed = Files.exists(Path.of("mods"));
        Files.createDirectories(dir);
        Path file = dir.resolve("selftest_mod.txt");
        Files.writeString(file, "name Self test mod\nstart 1950\nmaps random\n");
        try {
            mapgame.sim.Scenario s = mapgame.sim.Scenario.find("selftest_mod");
            check(s != null && s.startYear == 1950 && s.supports(MapKind.RANDOM) && !s.supports(MapKind.EUROPE), "scenarios are loaded from the mods folder");
            check(s != null && s.label().equals("Self test mod"), "a scenario without a text key shows its file name");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
            if (!existed) Files.deleteIfExists(Path.of("mods"));
        }
        check(mapgame.sim.Scenario.find("selftest_mod") == null, "removed mods are gone again");
    }

    private static void navy() {
        Game g = newGame();
        g.armies.clear();
        // A nation with a port, and an enemy with a port that touches the same sea.
        Province port = null, sea = null;
        for (Province p : g.world.provinces) {
            if (!mapgame.sim.Navy.isPort(p) || p.owner == null) continue;
            for (Province q : p.neighbors) if (!q.isLand()) {
                port = p;
                sea = q;
            }
            if (port != null) break;
        }
        check(port != null, "the map has a port");
        if (port == null) return;
        Country a = port.owner;
        g.player = a;
        a.gold = 1000;
        a.manpower = 5000;
        mapgame.model.Fleet f = mapgame.sim.Navy.recruit(g, a, port);
        check(f != null && f.inPort() && a.gold == 1000 - mapgame.sim.Navy.FLEET_GOLD, "a fleet can be built in a port");
        check(mapgame.sim.Navy.recruit(g, a, sea) == null, "no fleets are built at sea");
        double upkeep = g.monthlyUpkeep(a);
        check(upkeep > 0, "fleets cost upkeep");

        check(mapgame.sim.Navy.orderMove(g, f, sea), "a fleet can sail to the sea next to its port");
        for (int i = 0; i < 4; i++) g.tickDay();
        check(f.location == sea, "the fleet arrives");

        // Transport: load an army in port, sail, land again.
        mapgame.sim.Navy.orderMove(g, f, port);
        for (int i = 0; i < 4; i++) g.tickDay();
        check(f.location == port, "the fleet returns to port");
        Army army = army(g, a, port, 1500);
        check(mapgame.sim.Navy.embark(g, army, f), "an army can embark in port");
        check(!mapgame.sim.Navy.embark(g, army(g, a, port, 3000), f), "a fleet only carries what fits (" + f.capacity() + " men)");
        mapgame.sim.Navy.orderMove(g, f, sea);
        for (int i = 0; i < 4; i++) g.tickDay();
        check(army.location == sea && army.fleet == f, "the army travels with the fleet");
        check(g.orderMove(army, port) == false, "an embarked army takes no land orders");
        check(mapgame.sim.Navy.disembark(g, f, port) == 1 && army.fleet == null && army.location == port, "the army lands in the port");

        // Fleet battle and sinking with cargo.
        Country b = null;
        for (Country c : g.world.countries) if (c != a && c.alive) b = c;
        g.declareWar(a, b);
        mapgame.model.Fleet strong = f;
        strong.strength = 900;
        mapgame.sim.Navy.orderMove(g, strong, sea);
        for (int i = 0; i < 4; i++) g.tickDay();
        mapgame.model.Fleet weak = new mapgame.model.Fleet(900_000, b, sea, 300);
        g.fleets.add(weak);
        Army cargo = army(g, b, sea, 500);
        cargo.fleet = weak;
        weak.cargo.add(cargo);
        for (int i = 0; i < 12 && !weak.dead; i++) g.tickDay();
        check(weak.dead && !strong.dead, "the stronger fleet wins the battle");
        check(cargo.dead, "the cargo of a sunk fleet is lost");

        // Blockade of an enemy coast.
        Province coast = null;
        Country enemy = null;
        for (Province p : sea.neighbors) if (p.isLand() && p.coastal && p.owner != null && p.owner != a) {
            coast = p;
            enemy = p.owner;
        }
        if (coast != null) {
            g.declareWar(a, enemy);
            double before = g.monthlyIncome(enemy);
            g.tickDay();
            check(g.isBlockaded(coast), "an enemy fleet blockades the adjacent coast");
            check(g.monthlyIncome(enemy) < before, "a blockade cuts the income");
        }
    }

    private static void mapEditor() throws IOException {
        mapgame.sim.MapEditor ed = new mapgame.sim.MapEditor(MapKind.RANDOM, 42);
        Country atlantis = ed.newCountry("Atlantis", 0x8844AA);
        check(atlantis != null && ed.newCountry("Atlantis", 1) == null, "new nations need a unique name");
        check(ed.newCountry("Bad,Name", 1) == null && ed.newCountry("A=B", 1) == null, "names may not contain scenario syntax");
        Province land = null;
        for (Province p : ed.game.world.provinces) if (p.isLand() && p.owner != null && p.owner.provinces.size() > 3) land = p;
        check(land != null && ed.assign(land, atlantis), "a province can be given to a nation");
        check(land.owner == atlantis && atlantis.capital == land, "the new nation gets a capital");
        Country other = null;
        for (Country c : ed.nations()) if (c != atlantis) other = c;
        String oldName = other.name;
        check(ed.rename(other, "Renamed Land"), "nations can be renamed");
        ed.setName(land, "Port Atlantis");
        ed.setPopulation(land, 123_456);

        String text = ed.export("Editor test", 1920);
        check(text.contains("seed 42") && text.contains("newcountry Atlantis 8844AA") && text.contains("owner " + land.id + " Atlantis"), "the export lists the changes");
        check(text.contains("rename " + oldName + " = Renamed Land") && text.contains("provname " + land.id + " Port Atlantis"), "the export lists renames");

        mapgame.sim.Scenario sc = mapgame.sim.Scenario.parseForTest("editor_test", text);
        check(sc.seed != null && sc.seed == 42 && sc.startYear == 1920, "the exported scenario can be read back");
        Game g = Game.create(MapKind.RANDOM, sc.seed, sc);
        Province again = g.world.provinces.get(land.id);
        check(again.owner != null && again.owner.name.equals("Atlantis"), "the scenario recreates the new owner");
        check(again.name.equals("Port Atlantis") && again.population == 123_456, "the scenario recreates names and population");
        check(g.world.countries.stream().anyMatch(c -> c.alive && c.name.equals("Renamed Land")), "the scenario recreates renamed nations");
        for (int i = 0; i < 100; i++) g.tickDay();
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
