package mapgame.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mapgame.i18n.I18n;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.ArmyType;
import mapgame.model.Building;
import mapgame.model.Country;
import mapgame.model.MapGenerator;
import mapgame.model.MapKind;
import mapgame.model.Policy;
import mapgame.model.Province;
import mapgame.model.RealMapGenerator;
import mapgame.model.TechField;
import mapgame.model.World;

/** The whole simulation: calendar, wars, armies, economy. */
public final class Game {
    public static final int RECRUIT_GOLD = 40;
    public static final int RECRUIT_MEN = 1000;
    public static final double UPKEEP_PER_MAN = 0.005;
    public static final int OCCUPY_DAYS = 4;
    public static final int MIN_SPLIT = 200;
    public static final int TRUCE_DAYS = 720;
    /** Share of the loser's provinces a winner may take in a normal peace deal. */
    public static final double PEACE_SHARE = 0.35;

    public final World world;
    public final Random rnd;
    /** Map type and seed: together they recreate the exact same world when loading a save. */
    public final MapKind kind;
    public final long seed;
    public final List<Army> armies = new ArrayList<>();
    /** The human player's nation; null while choosing (or when running AI-only). */
    public Country player;
    public int day;
    /** Set whenever ownership/control changes so the renderer can rebuild the map. */
    public boolean mapDirty = true;
    public Consumer<String> logSink = s -> { };
    /** Statistics for the balance tools; not part of a save. */
    public int warsDeclared, totalWarsDeclared, peaceDeals, eliminations, coalitionWars, alliancesFormed;

    final Map<Long, Integer> wars = new HashMap<>();
    final Set<Long> totalWars = new HashSet<>();
    public final List<ArmyGroup> groups = new ArrayList<>();
    int nextGroupId = 1;
    int nextArmyId = 1;

    public Game(long seed) {
        this(MapGenerator.generate(seed), seed, MapKind.RANDOM);
    }

    /** Builds a game on the given map type (random or real geography). */
    public static Game create(MapKind kind, long seed) {
        return kind == MapKind.RANDOM ? new Game(seed) : new Game(RealMapGenerator.generate(kind, seed), seed, kind);
    }

    private Game(World world, long seed, MapKind kind) {
        this.world = world;
        this.kind = kind;
        this.seed = seed;
        this.rnd = new Random(seed ^ 0x9E3779B97F4A7C15L);
        for (Country c : world.countries) {
            c.gold = 300;
            c.manpower = 5000;
            // The starting force must be affordable: one 2000-man army costs 10 gold/month.
            int affordable = (int) (monthlyIncome(c) * 0.6 / (2000 * UPKEEP_PER_MAN));
            int count = Math.max(2, Math.min(c.provinces.size() / 6, affordable));
            int each = affordable >= count ? 2000 : Math.max(800, 2000 * affordable / count);
            for (int i = 0; i < count; i++) {
                Province p = i == 0 ? c.capital : c.provinces.get(rnd.nextInt(c.provinces.size()));
                armies.add(new Army(nextArmyId++, c, p, each));
            }
        }
        mergeArmies();
        world.recomputeLabels();
    }

    // ---------------------------------------------------------------- calendar / log

    public static String formatDate(int day) {
        return I18n.tr("date.format", day % 30 + 1, I18n.tr("month." + (day % 360) / 30), 1900 + day / 360);
    }

    public String date() {
        return formatDate(day);
    }

    public void log(String msg) {
        logSink.accept(date() + "  " + msg);
    }

    /** A noteworthy happening for the notification list; {@code where} (may be null) is the place to jump to. */
    public record Event(int day, String text, Province where) {
    }

    public static final int MAX_EVENTS = 300;
    public final List<Event> events = new ArrayList<>();
    public Consumer<Event> eventSink = e -> { };

    /** Asks the player how to react to an event; the default always takes the first option. */
    public interface DecisionHandler {
        int choose(String title, String text, List<String> options);
    }

    public DecisionHandler decisions = (title, text, options) -> 0;

    /**
     * Logs the message and, if the player is one of {@code involved} (or nobody is named: world news),
     * also files it in the notification list.
     */
    public void report(String msg, Province where, Country... involved) {
        log(msg);
        boolean relevant = involved.length == 0;
        for (Country c : involved) if (c != null && c == player) relevant = true;
        if (!relevant) return;
        Event e = new Event(day, msg, where);
        events.add(e);
        if (events.size() > MAX_EVENTS) events.remove(0);
        eventSink.accept(e);
    }

    // ---------------------------------------------------------------- diplomacy

    private static long key(Country a, Country b) {
        return ((long) Math.min(a.id, b.id) << 32) | Math.max(a.id, b.id);
    }

    public boolean atWar(Country a, Country b) {
        return a != null && b != null && a != b && wars.containsKey(key(a, b));
    }

    public int warStart(Country a, Country b) {
        return wars.getOrDefault(key(a, b), day);
    }

    public List<Country> enemiesOf(Country c) {
        List<Country> out = new ArrayList<>();
        for (Country o : world.countries) if (o.alive && atWar(c, o)) out.add(o);
        return out;
    }

    public boolean declareWar(Country a, Country b) {
        return declareWar(a, b, false);
    }

    /** A total war can never end in peace: only the elimination of one side stops it. */
    public boolean declareWar(Country a, Country b, boolean total) {
        return declareWar(a, b, total, true);
    }

    private boolean declareWar(Country a, Country b, boolean total, boolean callAllies) {
        if (a == b || !a.alive || !b.alive || allied(a, b)) return false;
        if (atWar(a, b)) {
            if (!total || isTotalWar(a, b)) return false;
            totalWars.add(key(a, b));
            report(I18n.tr("war.escalated", a.displayName(), b.displayName()), null, a, b);
            return true;
        }
        wars.put(key(a, b), day);
        warsDeclared++;
        if (total) {
            totalWars.add(key(a, b));
            totalWarsDeclared++;
        }
        report(I18n.tr(total ? "war.declared.total" : "war.declared", a.displayName(), b.displayName()), null, a, b);
        if (callAllies) callToArms(a, b);
        return true;
    }

    // ---------------------------------------------------------------- alliances

    /** Most allies a nation can have at the same time. */
    public static final int MAX_ALLIES = 3;
    /** Chance that an AI ally of the defender / of the aggressor joins a new war. */
    private static final double DEFENDER_ALLY_JOINS = 0.75, AGGRESSOR_ALLY_JOINS = 0.35;

    final Set<Long> alliances = new HashSet<>();

    public boolean allied(Country a, Country b) {
        return a != null && b != null && a != b && alliances.contains(key(a, b));
    }

    public List<Country> alliesOf(Country c) {
        List<Country> out = new ArrayList<>();
        for (Country o : world.countries) if (o.alive && allied(c, o)) out.add(o);
        return out;
    }

    /** Own land or land of an ally: armies move and are supplied there. */
    public boolean friendly(Country c, Country other) {
        return other == c || allied(c, other);
    }

    /** Allies of the attacked nation are called to arms; AI nations join most of the time, the player is notified. */
    private void callToArms(Country aggressor, Country victim) {
        for (Country ally : alliesOf(victim)) {
            if (ally == aggressor || atWar(ally, aggressor) || allied(ally, aggressor)) continue;
            if (ally == player) {
                report(I18n.tr("alliance.callarms", victim.displayName(), aggressor.displayName()), null, player);
            } else if (rnd.nextDouble() < DEFENDER_ALLY_JOINS) {
                declareWar(ally, aggressor, false, false);
            }
        }
        for (Country ally : alliesOf(aggressor)) {
            if (ally == victim || atWar(ally, victim) || allied(ally, victim) || ally == player) continue;
            if (rnd.nextDouble() < AGGRESSOR_ALLY_JOINS) declareWar(ally, victim, false, false);
        }
    }

    /** Alliance proposal. Returns a message describing the outcome. */
    public String proposeAlliance(Country from, Country to) {
        if (allied(from, to)) return I18n.tr("alliance.already", to.displayName());
        if (atWar(from, to)) return I18n.tr("alliance.atwar", to.displayName());
        if (alliesOf(from).size() >= MAX_ALLIES) return I18n.tr("alliance.full.us");
        if (alliesOf(to).size() >= MAX_ALLIES) return I18n.tr("alliance.full", to.displayName());
        if (!wouldAcceptAlliance(from, to)) return I18n.tr("alliance.refuses", to.displayName());
        makeAlliance(from, to);
        return I18n.tr("alliance.accepts", to.displayName());
    }

    /** The other side agrees to join against a common enemy or a hegemon, or when the proposer is the stronger one. */
    boolean wouldAcceptAlliance(Country from, Country to) {
        return sharedInterest(from, to) || strength(from) > strength(to) * 1.2 && to.provinces.size() <= from.provinces.size();
    }

    /** Both fight the same enemy, or both border an over-mighty nation. */
    boolean sharedInterest(Country a, Country b) {
        for (Country e : enemiesOf(a)) if (atWar(b, e)) return true;
        Country h = hegemon();
        return h != null && h != a && h != b && bordersCountry(a, h) && bordersCountry(b, h);
    }

    boolean bordersCountry(Country a, Country b) {
        for (Province p : a.provinces) for (Province q : p.neighbors) if (q.owner == b) return true;
        return false;
    }

    void makeAlliance(Country a, Country b) {
        if (!alliances.add(key(a, b))) return;
        alliancesFormed++;
        report(I18n.tr("alliance.formed", a.displayName(), b.displayName()), null, a, b);
    }

    public void breakAlliance(Country a, Country b) {
        if (!alliances.remove(key(a, b))) return;
        report(I18n.tr("alliance.broken", a.displayName(), b.displayName()), null, a, b);
        evictArmies();
    }

    public boolean isTotalWar(Country a, Country b) {
        return a != null && b != null && totalWars.contains(key(a, b));
    }

    /** Positive when {@code a} holds more of b's land than b holds of a's. */
    public int warScore(Country a, Country b) {
        int score = 0;
        for (Province p : b.provinces) if (p.controller == a) score++;
        for (Province p : a.provinces) if (p.controller == b) score--;
        return score;
    }

    /**
     * Ends the war. Each winner receives at most {@link #PEACE_SHARE} of the loser's land (the most valuable
     * occupied provinces next to its own territory first); every other occupied province is handed back.
     */
    public void makePeace(Country a, Country b) {
        if (!atWar(a, b) || isTotalWar(a, b)) return;
        int ownedA = a.provinces.size(), ownedB = b.provinces.size();
        int moved = transferOnPeace(a, b, ownedB) + transferOnPeace(b, a, ownedA);
        wars.remove(key(a, b));
        peaceDeals++;
        relocateCapital(a);
        relocateCapital(b);
        a.truceUntil = Math.max(a.truceUntil, day + TRUCE_DAYS);
        b.truceUntil = Math.max(b.truceUntil, day + TRUCE_DAYS);
        report(I18n.tr("peace.made", a.displayName(), b.displayName()) + (moved == 0 ? "" : " " + I18n.tr("peace.moved", moved)), null, a, b);
        evictArmies();
        checkElimination(a);
        checkElimination(b);
        mapDirty = true;
    }

    /** A nation that lost its capital in a peace deal moves the capital to its most populous province. */
    private void relocateCapital(Country c) {
        if (!c.alive || c.capital == null || c.capital.owner == c) return;
        Province best = null;
        for (Province p : c.provinces) {
            if (best == null || p.population * p.wealth() > best.population * best.wealth()) best = p;
        }
        if (best != null) {
            c.capital = best;
            report(I18n.tr("capital.moved", c.displayName(), best.name), best, c);
        }
    }

    private int transferOnPeace(Country winner, Country loser, int loserOwned) {
        List<Province> held = new ArrayList<>();
        for (Province p : loser.provinces) if (p.controller == winner) held.add(p);
        if (held.isEmpty()) return 0;
        // A normal war never takes a nation's last province: only a total war can erase it.
        int quota = Math.max(0, Math.min(loserOwned - 1, (int) Math.ceil(loserOwned * PEACE_SHARE)));
        held.sort((x, y) -> Double.compare(peaceValue(y, winner), peaceValue(x, winner)));
        int moved = 0;
        for (Province p : held) {
            if (moved < quota) {
                world.setOwner(p, winner);
                p.conqueredDay = day;
                moved++;
            } else {
                p.controller = loser;
            }
        }
        return moved;
    }

    private static double peaceValue(Province p, Country winner) {
        double v = p.population;
        for (Province q : p.neighbors) {
            if (q.owner == winner && q.controller == winner) {
                v += 1_000_000;
                break;
            }
        }
        return v;
    }

    /** Player peace proposal. Returns a message describing the outcome. */
    public String proposePeace(Country from, Country to) {
        if (!atWar(from, to)) return I18n.tr("peace.notwar");
        if (isTotalWar(from, to)) return I18n.tr("peace.total");
        boolean accept = warScore(to, from) <= 0 || day - warStart(from, to) > 300;
        if (!accept) return I18n.tr("peace.refuses", to.displayName());
        makePeace(from, to);
        return I18n.tr("peace.accepts", to.displayName());
    }

    // ---------------------------------------------------------------- queries

    public boolean allowed(Country c, Province p) {
        if (!p.isLand()) return false;
        if (p.owner == null || p.owner == c || p.controller == c) return true;
        if (allied(c, p.owner) || allied(c, p.controller)) return true;
        return atWar(c, p.owner) || atWar(c, p.controller);
    }

    public List<Army> armiesOf(Country c) {
        List<Army> out = new ArrayList<>();
        for (Army a : armies) if (a.owner == c) out.add(a);
        return out;
    }

    public long strength(Country c) {
        long s = 0;
        for (Army a : armies) if (a.owner == c) s += a.strength;
        return s;
    }

    public boolean isEngaged(Army a) {
        for (Army o : armies) {
            if (o != a && !o.dead && o.location == a.location && atWar(a.owner, o.owner)) return true;
        }
        return false;
    }

    public Map<Province, Integer> enemyStrengthByProvince(Country c) {
        Map<Province, Integer> m = new HashMap<>();
        for (Army a : armies) if (atWar(c, a.owner)) m.merge(a.location, a.strength, Integer::sum);
        return m;
    }

    public double monthlyIncome(Country c) {
        double inc = 0;
        for (Province p : c.provinces) if (p.controller == c) inc += p.income(day);
        double bonus = day < c.incomeBonusUntil ? 1 + c.incomeBonus : 1;
        return inc * (1 + 0.08 * c.techLevel(TechField.ECONOMY)) * c.taxes.gold * c.conscription.gold * bonus;
    }

    public double monthlyUpkeep(Country c) {
        double u = 0;
        for (Army a : armies) if (a.owner == c) u += a.strength * UPKEEP_PER_MAN * a.type.upkeepFactor;
        return u * (1 - 0.04 * c.techLevel(TechField.ADMIN));
    }

    public int monthlyManpower(Country c) {
        int mp = 0;
        for (Province p : c.provinces) if (p.controller == c) mp += p.manpowerGain(day);
        return (int) (mp * (1 + 0.1 * c.techLevel(TechField.ADMIN)) * c.taxes.manpower * c.conscription.manpower);
    }

    /** Days until the nation may change its policies again (0 = now). */
    public int policyCooldown(Country c) {
        return Math.max(0, c.policyChangedDay + Policy.CHANGE_DELAY - day);
    }

    /** Changes the policies unless they were changed recently; unchanged values never start the cooldown. */
    public boolean setPolicies(Country c, Policy.Taxes taxes, Policy.Conscription conscription) {
        if (taxes == c.taxes && conscription == c.conscription) return true;
        if (policyCooldown(c) > 0) return false;
        c.taxes = taxes;
        c.conscription = conscription;
        c.policyChangedDay = day;
        return true;
    }

    // ---------------------------------------------------------------- research

    /** Research points a nation earns per month: bigger nations and administrative progress research faster. */
    public double researchRate(Country c) {
        return Math.min(5.0, 1 + c.provinces.size() * 0.08) * (1 + 0.1 * c.techLevel(TechField.ADMIN));
    }

    /** Points still missing for the current project. */
    public int researchCost(Country c, TechField f) {
        return TechField.cost(c.techLevel(f));
    }

    /** Starts researching {@code f}; switching to another field discards the progress made so far. */
    public boolean startResearch(Country c, TechField f) {
        if (f == null || c.techLevel(f) >= TechField.MAX_LEVEL) return false;
        if (c.researching != f) c.researchProgress = 0;
        c.researching = f;
        return true;
    }

    private void researchTick(Country c) {
        if (c.researching == null) {
            if (c != player) aiPickResearch(c);
            if (c.researching == null) return;
        }
        c.researchProgress += researchRate(c);
        TechField f = c.researching;
        if (c.researchProgress < researchCost(c, f)) return;
        c.tech[f.ordinal()]++;
        c.researchProgress = 0;
        c.researching = null;
        report(I18n.tr("tech.done", c.displayName(), f.label(), c.techLevel(f)), null, c);
    }

    /** AI research: military first while at war, otherwise economy and administration. */
    private void aiPickResearch(Country c) {
        boolean atWar = !enemiesOf(c).isEmpty();
        TechField[] order = atWar
                ? new TechField[] {TechField.MILITARY, TechField.ECONOMY, TechField.ADMIN}
                : new TechField[] {TechField.ECONOMY, TechField.ADMIN, TechField.MILITARY};
        TechField best = null;
        for (TechField f : order) {
            if (c.techLevel(f) >= TechField.MAX_LEVEL) continue;
            if (best == null || c.techLevel(f) + 1 < c.techLevel(best)) best = f;
        }
        if (best != null) startResearch(c, best);
    }

    // ---------------------------------------------------------------- orders

    public Army recruit(Country c, Province p) {
        return recruit(c, p, ArmyType.INFANTRY);
    }

    /** Gold a fresh army of this type costs. */
    public static int recruitGold(ArmyType type) {
        return (int) Math.ceil(RECRUIT_GOLD * type.goldFactor);
    }

    public Army recruit(Country c, Province p, ArmyType type) {
        if (!p.isLand() || p.owner != c || p.controller != c) return null;
        int gold = recruitGold(type);
        if (c.gold < gold || c.manpower < RECRUIT_MEN) return null;
        c.gold -= gold;
        c.manpower -= RECRUIT_MEN;
        Army a = new Army(nextArmyId++, c, p, RECRUIT_MEN, type);
        armies.add(a);
        return a;
    }

    public boolean canSplit(Army a) {
        return a != null && !a.dead && a.progress == 0 && a.strength >= MIN_SPLIT;
    }

    /** Splits off half of the army into a new army in the same province; returns it or null. */
    public Army split(Army a) {
        if (!canSplit(a)) return null;
        int half = a.strength / 2;
        a.strength -= half;
        Army n = new Army(nextArmyId++, a.owner, a.location, half, a.type);
        n.experience = a.experience;
        n.morale = a.morale;
        armies.add(n);
        if (a.group != null) assignToGroup(a.group, n);
        return n;
    }

    /** Number of idle friendly armies in the same province that {@link #mergeStack} would absorb. */
    public int mergeCandidates(Army a) {
        if (a == null || a.dead || a.progress != 0) return 0;
        int n = 0;
        for (Army o : armies) if (isMergeable(a, o)) n++;
        return n;
    }

    /** Merges all idle friendly armies of the same type in the same province into {@code a}. */
    public int mergeStack(Army a) {
        int merged = 0;
        if (a == null || a.dead || a.progress != 0) return 0;
        for (Army o : armies) {
            if (!isMergeable(a, o)) continue;
            a.absorb(o);
            if (a.group == null && o.group != null) assignToGroup(o.group, a);
            o.dead = true;
            merged++;
        }
        armies.removeIf(o -> o.dead);
        return merged;
    }

    private static boolean isMergeable(Army a, Army o) {
        return o != a && !o.dead && o.owner == a.owner && o.type == a.type && o.location == a.location && o.path.isEmpty();
    }

    // ---------------------------------------------------------------- army groups

    public ArmyGroup createGroup(Country owner) {
        ArmyGroup g = new ArmyGroup(nextGroupId++, owner);
        groups.add(g);
        return g;
    }

    public void deleteGroup(ArmyGroup g) {
        for (Army a : g.armies) a.group = null;
        g.armies.clear();
        groups.remove(g);
    }

    public boolean assignToGroup(ArmyGroup g, Army a) {
        if (a == null || a.dead || a.owner != g.owner) return false;
        if (a.group != null) a.group.armies.remove(a);
        a.group = g;
        g.armies.add(a);
        return true;
    }

    public void releaseFromGroup(Army a) {
        if (a == null || a.group == null) return;
        a.group.armies.remove(a);
        a.group.slots.remove(a);
        a.group = null;
    }

    /** Gold a single refilled soldier costs: the same price as in a fresh recruit. */
    public static final double GOLD_PER_MAN = (double) RECRUIT_GOLD / RECRUIT_MEN;
    /** Most men a group tops up in one army per planning round. */
    public static final int REFILL_STEP = 500;
    /** Gold that automatic reinforcement leaves untouched so upkeep can still be paid. */
    public static final int RESERVE_GOLD = 50;

    /**
     * Brings a group with {@link ArmyGroup#reinforce} back towards its {@link ArmyGroup#goal}: armies standing in
     * own territory are refilled from the manpower pool, and when that is not enough a new army is raised at home.
     */
    void reinforce(ArmyGroup grp) {
        Country c = grp.owner;
        if (!c.alive) return;
        int deficit = grp.goal - grp.strength();
        for (Army a : grp.armies) {
            if (deficit <= 0) break;
            if (a.dead || a.location.controller != c || a.location.owner != c || isEngaged(a)) continue;
            int rate = Math.max(1, (int) Math.ceil(GOLD_PER_MAN * a.type.goldFactor * 1000));
            int affordable = (int) (Math.max(0, c.gold - RESERVE_GOLD) * 1000 / rate);
            int add = Math.min(Math.min(REFILL_STEP, deficit), Math.min(c.manpower, affordable));
            if (add <= 0) return;
            a.addMen(add);
            c.manpower -= add;
            c.gold -= add * rate / 1000.0;
            deficit -= add;
        }
        if (deficit < RECRUIT_MEN || c.gold < RECRUIT_GOLD + RESERVE_GOLD || c.manpower < RECRUIT_MEN) return;
        Province site = reinforcementSite(grp);
        if (site == null) return;
        Army n = recruit(c, site);
        if (n != null) assignToGroup(grp, n);
    }

    /** The own, unoccupied province closest to the group's front (or its armies) where new troops are raised. */
    private Province reinforcementSite(ArmyGroup grp) {
        double fx = 0, fy = 0;
        int n = 0;
        List<Province> anchors = grp.front.isEmpty() ? List.of() : grp.front;
        for (Province p : anchors) {
            fx += p.x;
            fy += p.y;
            n++;
        }
        if (n == 0) {
            for (Army a : grp.armies) {
                if (a.dead) continue;
                fx += a.location.x;
                fy += a.location.y;
                n++;
            }
        }
        Province best = null;
        double bd = Double.MAX_VALUE;
        for (Province p : grp.owner.provinces) {
            if (p.controller != grp.owner || !p.isLand()) continue;
            double d = n == 0 ? 0 : (p.x - fx / n) * (p.x - fx / n) + (p.y - fy / n) * (p.y - fy / n);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Re-plans a group right away instead of waiting for the next planning tick. */
    public void planGroup(ArmyGroup g) {
        FrontPlanner.update(this, g);
    }

    /** Manual order from the player; takes the army out of its army group. */
    public boolean orderMove(Army a, Province dest) {
        if (a == null || a.dead || dest == null || !dest.isLand()) return false;
        if (!moveArmy(a, dest)) return false;
        if (a.group != null) {
            log(I18n.tr("army.removed", a.owner.displayName(), a.group.displayName()));
            releaseFromGroup(a);
        }
        return true;
    }

    boolean moveArmy(Army a, Province dest) {
        if (dest == a.location) {
            a.clearPath();
            return true;
        }
        List<Province> path = findPath(a.owner, a.location, p -> p == dest);
        if (path == null) return false;
        a.setPath(path);
        return true;
    }

    /** Cheapest route (by travel days) to the nearest province matching {@code goal}, or null. */
    public List<Province> findPath(Country c, Province from, Predicate<Province> goal) {
        int n = world.provinces.size();
        double[] dist = new double[n];
        Arrays.fill(dist, Double.MAX_VALUE);
        Province[] prev = new Province[n];
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));
        dist[from.id] = 0;
        pq.add(new double[] {0, from.id});
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            Province p = world.provinces.get((int) cur[1]);
            if (cur[0] > dist[p.id]) continue;
            if (p != from && goal.test(p)) {
                LinkedList<Province> path = new LinkedList<>();
                for (Province q = p; q != from; q = prev[q.id]) path.addFirst(q);
                return path;
            }
            for (Province q : p.neighbors) {
                if (!allowed(c, q)) continue;
                double nd = cur[0] + q.travelDays(p);
                if (nd < dist[q.id]) {
                    dist[q.id] = nd;
                    prev[q.id] = p;
                    pq.add(new double[] {nd, q.id});
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- buildings and unrest

    /** Unrest gained per day while an enemy holds a province without a garrison, and lost per day with one. */
    public static final double UNREST_RISE = 0.4, UNREST_GARRISON = -0.3, UNREST_CALM = 1.0;

    /** Gold the next level of {@code b} costs in {@code p}, or -1 when it cannot be built. */
    public int buildCost(Province p, Building b) {
        if (p == null || !p.isLand() || p.owner == null || p.building != null) return -1;
        int level = p.level(b);
        return level >= b.maxLevel ? -1 : b.cost(level);
    }

    /** Starts construction; the owner must hold the province. */
    public boolean build(Country c, Province p, Building b) {
        int cost = buildCost(p, b);
        if (cost < 0 || p.owner != c || p.controller != c || c.gold < cost) return false;
        c.gold -= cost;
        p.building = b;
        p.buildDone = day + Building.BUILD_DAYS;
        return true;
    }

    private void updateProvinces() {
        for (Province p : world.provinces) {
            if (p.owner == null) continue;
            if (p.building != null && day >= p.buildDone) {
                p.buildings[p.building.ordinal()]++;
                if (p.owner == player) report(I18n.tr("building.done", p.building.label(), p.name), p, p.owner);
                p.building = null;
            }
            if (p.controller == p.owner) {
                if (p.unrest > 0) p.unrest = Math.max(0, p.unrest - UNREST_CALM);
                continue;
            }
            boolean garrison = false;
            for (Army a : armies) {
                if (!a.dead && a.location == p && a.owner == p.controller) {
                    garrison = true;
                    break;
                }
            }
            p.unrest = Math.max(0, Math.min(100, p.unrest + (garrison ? UNREST_GARRISON : UNREST_RISE)));
            if (p.unrest >= 100) {
                Country holder = p.controller;
                p.controller = p.owner;
                p.unrest = 0;
                mapDirty = true;
                report(I18n.tr("rebels.free", p.name, p.owner.displayName(), holder.displayName()), p, p.owner, holder);
            }
        }
    }

    // ---------------------------------------------------------------- fog of war

    /** When set, the player only sees foreign armies near his own land and armies (see {@link #visibleTo}). */
    public boolean fogOfWar;

    /**
     * Provinces a nation can currently see: its own and allied land, its own and allied armies, and everything
     * one province further (two for cavalry).
     */
    public boolean[] visibleTo(Country c) {
        boolean[] vis = new boolean[world.provinces.size()];
        for (Province p : world.provinces) {
            if (friendly(c, p.controller) && p.controller != null || friendly(c, p.owner) && p.owner != null) reveal(p, 1, vis);
        }
        for (Army a : armies) {
            if (!a.dead && friendly(c, a.owner)) reveal(a.location, a.type == ArmyType.CAVALRY ? 2 : 1, vis);
        }
        return vis;
    }

    private static void reveal(Province p, int radius, boolean[] vis) {
        vis[p.id] = true;
        if (radius == 0) return;
        for (Province q : p.neighbors) reveal(q, radius - 1, vis);
    }

    // ---------------------------------------------------------------- supply and attrition

    /** Farthest distance (in provinces) from own land that still counts in the supply calculation. */
    public static final int MAX_SUPPLY_DEPTH = 4;
    /** Armies larger than this lose extra men in the field. */
    public static final int STACK_FREE = 4000;

    /**
     * How many provinces an army is away from land its own nation controls: 0 = standing on it, 1 = next to it (fully
     * supplied), up to {@link #MAX_SUPPLY_DEPTH} + 1 when cut off.
     */
    public int supplyDepth(Army a) {
        Country c = a.owner;
        if (friendly(c, a.location.controller)) return 0;
        Set<Province> seen = new HashSet<>();
        List<Province> frontier = new ArrayList<>();
        frontier.add(a.location);
        seen.add(a.location);
        for (int depth = 1; depth <= MAX_SUPPLY_DEPTH; depth++) {
            List<Province> next = new ArrayList<>();
            for (Province p : frontier) {
                for (Province q : p.neighbors) {
                    if (!q.isLand() || !seen.add(q)) continue;
                    if (friendly(c, q.controller)) return depth;
                    next.add(q);
                }
            }
            frontier = next;
        }
        return MAX_SUPPLY_DEPTH + 1;
    }

    /** True in the cold half of the year at high latitudes. */
    public boolean isWinterAt(Province p) {
        int month = (day % 360) / 30;
        double lat = p.lat();
        return lat > 35 && (month == 11 || month <= 1) || lat < -35 && month >= 5 && month <= 7;
    }

    /**
     * Share of its men an army loses per 5 days: none at home or right behind its own border, then growing with the
     * distance from own land, the terrain, the size of the stack and winter.
     */
    public double attritionRate(Army a) {
        int depth = supplyDepth(a);
        if (depth <= 1) return 0;
        double distance = depth == 2 ? 1.0 : depth == 3 ? 1.5 : 2.5;
        double stack = 1 + Math.max(0, a.strength - STACK_FREE) / 8000.0;
        double winter = isWinterAt(a.location) ? 1.6 : 1.0;
        return a.location.terrain.attrition * distance * stack * winter;
    }

    private void applyAttrition() {
        for (Army a : armies) {
            if (a.dead) continue;
            int loss = (int) Math.round(a.strength * attritionRate(a));
            if (loss <= 0) continue;
            a.strength -= loss;
            if (a.strength < 100) {
                a.dead = true;
                if (a.owner == player) report(I18n.tr("army.starved", a.location.name), a.location, a.owner);
            }
        }
    }

    // ---------------------------------------------------------------- simulation

    public void tickDay() {
        day++;
        moveArmies();
        resolveCombat();
        mergeArmies();
        resolveOccupation();
        if (day % 5 == 0) applyAttrition();
        updateProvinces();
        if (day % 3 == 0) {
            for (ArmyGroup grp : new ArrayList<>(groups)) {
                if (grp.reinforce) reinforce(grp);
                FrontPlanner.update(this, grp);
            }
        }
        if (day % 5 == 0) {
            for (Country c : new ArrayList<>(world.countries)) {
                if (c.alive && c != player) Ai.think(this, c);
            }
        }
        if (day % 30 == 0) monthly();
        armies.removeIf(a -> a.dead);
    }

    private void moveArmies() {
        for (Army a : armies) {
            if (a.dead) continue;
            a.daysHere++;
            if (a.path.isEmpty()) {
                a.progress = 0;
                continue;
            }
            if (isEngaged(a)) continue;
            Province next = a.path.peek();
            if (!allowed(a.owner, next)) {
                a.clearPath();
                continue;
            }
            a.progress += a.type.speed;
            if (a.progress >= next.travelDays(a.location)) {
                a.moveTo(next);
                a.path.poll();
                a.progress = 0;
            }
        }
    }

    private void resolveCombat() {
        Map<Province, List<Army>> byProvince = new LinkedHashMap<>();
        for (Army a : armies) if (!a.dead) byProvince.computeIfAbsent(a.location, k -> new ArrayList<>()).add(a);
        List<Army> broken = new ArrayList<>();
        Set<Army> fought = new HashSet<>();
        for (Map.Entry<Province, List<Army>> e : byProvince.entrySet()) {
            List<Army> list = e.getValue();
            if (list.size() < 2) continue;
            Province prov = e.getKey();
            double[] dmg = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Army target = list.get(i);
                double vulnerability = target.type.vulnerability * target.toughness();
                // Guns standing next to friendly foot soldiers are screened.
                if (target.type == ArmyType.ARTILLERY && screened(list, target)) vulnerability = target.toughness();
                for (int j = 0; j < list.size(); j++) {
                    Army striker = list.get(j);
                    if (i == j || !atWar(target.owner, striker.owner)) continue;
                    double d = striker.strength * 0.04 * (0.8 + 0.4 * rnd.nextDouble());
                    d *= striker.type.attackOn(prov.terrain) * striker.attackBonus();
                    if (target.owner == prov.controller) d /= prov.terrain.defense * (1 + 0.15 * prov.level(Building.FORT));
                    dmg[i] += d * vulnerability;
                }
            }
            for (int i = 0; i < list.size(); i++) {
                if (dmg[i] <= 0) continue;
                Army a = list.get(i);
                int before = a.strength;
                a.strength -= Math.max(1, (int) dmg[i]);
                a.experience = Math.min(Army.MAX_EXPERIENCE, a.experience + EXPERIENCE_PER_BATTLE_DAY);
                // Morale suffers with the share of the army lost; a good general keeps the men together.
                double shock = Math.min(15, 150.0 * (before - a.strength) / Math.max(1, before)) * (1 - 0.1 * a.generalSkill());
                a.morale = Math.max(0, a.morale - shock);
                fought.add(a);
                if (a.strength < 100 || a.morale <= Army.BREAK_MORALE) broken.add(a);
            }
        }
        for (Army a : armies) {
            if (a.dead || fought.contains(a) || a.morale >= 100) continue;
            double rest = a.location.controller == a.owner ? 3 : 1.5;
            a.morale = Math.min(100, a.morale + rest + 0.25 * a.generalSkill());
        }
        for (Army a : broken) retreat(a);
    }

    /** Experience points an army gains per day of fighting. */
    public static final double EXPERIENCE_PER_BATTLE_DAY = 1.0;

    private static boolean screened(List<Army> stack, Army gun) {
        for (Army o : stack) if (o != gun && o.owner == gun.owner && o.type == ArmyType.INFANTRY) return true;
        return false;
    }

    private void retreat(Army a) {
        Province best = null;
        for (Province q : a.location.neighbors) {
            if (!allowed(a.owner, q) || hostileArmyAt(a.owner, q)) continue;
            if (best == null || q.controller == a.owner) best = q;
        }
        boolean playerInvolved = a.owner == player;
        if (best == null || a.strength <= 0) {
            a.dead = true;
            if (playerInvolved) report(I18n.tr("army.destroyed", a.location.name), a.location, a.owner);
        } else {
            if (playerInvolved) report(I18n.tr("army.routed", a.location.name), a.location, a.owner);
            a.moveTo(best);
            a.clearPath();
            a.morale = Math.max(a.morale, Army.BREAK_MORALE + 15);
        }
    }

    private boolean hostileArmyAt(Country c, Province p) {
        for (Army o : armies) if (!o.dead && o.location == p && atWar(c, o.owner)) return true;
        return false;
    }

    private void mergeArmies() {
        Map<Long, Army> stacks = new HashMap<>();
        for (Army a : armies) {
            // The player's armies only merge on request so they can be split across provinces.
            if (a.dead || !a.path.isEmpty() || a.owner == player) continue;
            long k = ((long) a.location.id << 32) | ((long) a.owner.id << 4) | a.type.ordinal();
            Army host = stacks.get(k);
            if (host == null) {
                stacks.put(k, a);
            } else {
                host.absorb(a);
                a.dead = true;
            }
        }
        armies.removeIf(a -> a.dead);
    }

    private void resolveOccupation() {
        for (Army a : armies) {
            if (a.dead) continue;
            Province p = a.location;
            if (p.owner == null || p.controller == a.owner || !atWar(a.owner, p.controller)) continue;
            if (a.daysHere < OCCUPY_DAYS || hostileArmyAt(a.owner, p)) continue;
            Country victim = p.owner;
            p.controller = a.owner;
            mapDirty = true;
            if (a.owner == player) report(I18n.tr("occupied.us", p.name, victim.displayName()), p, player);
            else if (victim == player) report(I18n.tr("occupied.them", p.name, a.owner.displayName()), p, player);
            if (victim.capital == p && victim.alive) report(I18n.tr("capital.fallen", victim.displayName(), p.name, a.owner.displayName()), p, victim, a.owner);
            checkElimination(victim);
        }
    }

    /**
     * A country that holds none of its own provinces capitulates: normal wars end with limited demands
     * (see {@link #makePeace}). Only when total-war enemies or third parties hold everything is it eliminated.
     */
    private void checkElimination(Country c) {
        if (!c.alive || holdsOwnLand(c)) return;
        for (Country foe : enemiesOf(c)) {
            if (isTotalWar(c, foe)) continue;
            makePeace(c, foe);
            if (!c.alive || holdsOwnLand(c)) return;
        }
        for (Province p : new ArrayList<>(c.provinces)) if (p.controller != c) world.setOwner(p, p.controller);
        c.alive = false;
        eliminations++;
        for (Army a : armies) if (a.owner == c) a.dead = true;
        wars.keySet().removeIf(k -> (k >> 32) == c.id || (k & 0xFFFFFFFFL) == c.id);
        totalWars.removeIf(k -> (k >> 32) == c.id || (k & 0xFFFFFFFFL) == c.id);
        alliances.removeIf(k -> (k >> 32) == c.id || (k & 0xFFFFFFFFL) == c.id);
        mapDirty = true;
        report(I18n.tr("country.eliminated", c.displayName()), null);
    }

    private static boolean holdsOwnLand(Country c) {
        for (Province p : c.provinces) if (p.controller == c) return true;
        return false;
    }

    /** After peace, armies standing on land they may no longer enter are sent home. */
    private void evictArmies() {
        for (Army a : armies) {
            if (a.dead || allowed(a.owner, a.location)) continue;
            Country owner = a.owner;
            List<Province> home = findPath(owner, a.location, p -> p.owner == owner && p.controller == owner);
            if (home != null && !home.isEmpty()) {
                a.moveTo(home.get(home.size() - 1));
                a.clearPath();
            } else {
                a.dead = true;
            }
        }
    }

    private void monthly() {
        for (Country c : world.countries) {
            if (!c.alive) continue;
            c.gold += monthlyIncome(c) - monthlyUpkeep(c);
            c.manpower += monthlyManpower(c);
            researchTick(c);
            GameEvents.monthly(this, c);
            if (c.gold < 0) {
                for (Army a : armies) if (a.owner == c) a.strength = (int) (a.strength * 0.97);
            }
        }
    }

    public List<Country> aliveCountries() {
        List<Country> out = new ArrayList<>();
        for (Country c : world.countries) if (c.alive) out.add(c);
        return out;
    }

    /** Share of all owned land above which a nation counts as a hegemon the others band together against. */
    public static final double HEGEMON_SHARE = 0.20;

    /** The nation holding more than {@link #HEGEMON_SHARE} of all owned provinces, or null. */
    public Country hegemon() {
        int total = 0;
        Country best = null;
        for (Country c : world.countries) {
            if (!c.alive) continue;
            total += c.provinces.size();
            if (best == null || c.provinces.size() > best.provinces.size()) best = c;
        }
        return best != null && best.provinces.size() > total * HEGEMON_SHARE ? best : null;
    }

    /** Sends a share of a nation's soldiers home (they return to the manpower pool); used to get out of debt. */
    public void demobilize(Country c, double share) {
        for (Army a : armies) {
            if (a.dead || a.owner != c) continue;
            int released = (int) (a.strength * share);
            a.strength -= released;
            c.manpower += released;
            if (a.strength < 100) {
                c.manpower += a.strength;
                a.dead = true;
            }
        }
    }
}
