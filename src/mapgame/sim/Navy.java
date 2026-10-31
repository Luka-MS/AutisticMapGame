package mapgame.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import mapgame.i18n.I18n;
import mapgame.model.Army;
import mapgame.model.Country;
import mapgame.model.Fleet;
import mapgame.model.Province;

/**
 * Naval warfare: fleets, ports, sea movement, fleet battles, blockades and the transport of armies over sea.
 * All rules live here; {@link Game} calls {@link #tick} every day and {@link #ai} for AI nations.
 */
public final class Navy {
    public static final int FLEET_GOLD = 100, FLEET_MEN = 500, FLEET_STRENGTH = 500;
    /** Fleets cost this many times the upkeep of the same number of soldiers. */
    public static final double UPKEEP_FACTOR = 2.0;
    /** Share of a coastal province's income that is lost while it is blockaded. */
    public static final double BLOCKADE_LOSS = 0.5;
    private static final int SEA_DAYS = 2, PORT_DAYS = 1;

    private Navy() {
    }

    // ---------------------------------------------------------------- ports and routes

    /** A port is a coastal land province. */
    public static boolean isPort(Province p) {
        return p.isLand() && p.coastal;
    }

    /** Fleets may enter every sea province and every coastal province held by their nation or its allies. */
    static boolean canEnter(Game g, Country c, Province to) {
        if (!to.isLand()) return true;
        return to.coastal && to.controller != null && g.friendly(c, to.controller);
    }

    /** Days a fleet needs to enter {@code to}. */
    static int days(Province to) {
        return to.isLand() ? PORT_DAYS : SEA_DAYS;
    }

    /** Cheapest sea route to the nearest province satisfying {@code goal}, or null. */
    public static List<Province> path(Game g, Country c, Province from, Predicate<Province> goal) {
        int n = g.world.provinces.size();
        double[] dist = new double[n];
        Arrays.fill(dist, Double.MAX_VALUE);
        Province[] prev = new Province[n];
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));
        dist[from.id] = 0;
        pq.add(new double[] {0, from.id});
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            Province p = g.world.provinces.get((int) cur[1]);
            if (cur[0] > dist[p.id]) continue;
            if (p != from && goal.test(p)) {
                LinkedList<Province> path = new LinkedList<>();
                for (Province q = p; q != from; q = prev[q.id]) path.addFirst(q);
                return path;
            }
            for (Province q : p.neighbors) {
                // A fleet never leaves a port over land: every step touches the sea.
                if (p.isLand() && q.isLand()) continue;
                if (!canEnter(g, c, q)) continue;
                double nd = cur[0] + days(q);
                if (nd < dist[q.id]) {
                    dist[q.id] = nd;
                    prev[q.id] = p;
                    pq.add(new double[] {nd, q.id});
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- orders

    /** Raises a fleet in a port the nation owns and holds. */
    public static Fleet recruit(Game g, Country c, Province port) {
        if (port == null || !isPort(port) || port.owner != c || port.controller != c) return null;
        if (c.gold < FLEET_GOLD || c.manpower < FLEET_MEN) return null;
        c.gold -= FLEET_GOLD;
        c.manpower -= FLEET_MEN;
        Fleet f = new Fleet(g.nextFleetId++, c, port, FLEET_STRENGTH);
        g.fleets.add(f);
        return f;
    }

    /** Sends a fleet to a sea province or a friendly port. */
    public static boolean orderMove(Game g, Fleet f, Province dest) {
        if (f == null || f.dead || dest == null || dest == f.location) return false;
        if (dest.isLand() && !canEnter(g, f.owner, dest)) return false;
        List<Province> p = path(g, f.owner, f.location, q -> q == dest);
        if (p == null) return false;
        f.setPath(p);
        f.target = null;
        return true;
    }

    /** Puts an idle army that stands in the fleet's port on board (if it fits). */
    public static boolean embark(Game g, Army a, Fleet f) {
        if (a == null || f == null || a.dead || f.dead || a.fleet != null) return false;
        if (!f.inPort() || a.location != f.location || a.owner != f.owner || !a.path.isEmpty()) return false;
        if (g.isEngaged(a) || f.cargoMen() + a.strength > f.capacity()) return false;
        g.releaseFromGroup(a);
        a.fleet = f;
        f.cargo.add(a);
        return true;
    }

    /** Loads all idle armies from the fleet's port, splitting armies that do not fit. Returns the number of armies loaded. */
    public static int embarkAll(Game g, Fleet f) {
        if (f == null || f.dead || !f.inPort()) return 0;
        int loaded = 0;
        List<Army> candidates = new ArrayList<>();
        for (Army a : g.armies) {
            if (!a.dead && a.fleet == null && a.owner == f.owner && a.location == f.location && a.path.isEmpty()) candidates.add(a);
        }
        candidates.sort((x, y) -> y.strength - x.strength);
        for (Army a : candidates) {
            for (int guard = 0; guard < 6 && !a.dead; guard++) {
                int room = f.capacity() - f.cargoMen();
                if (a.strength <= room) {
                    if (embark(g, a, f)) loaded++;
                    break;
                }
                if (room < Game.MIN_SPLIT || g.split(a) == null) break;
            }
        }
        return loaded;
    }

    /**
     * Lands the whole cargo in {@code land}: the fleet's own port, or a land province next to the sea province it is in.
     * Landing in hostile territory is allowed; the armies have to fight for it. Returns the number of armies landed.
     */
    public static int disembark(Game g, Fleet f, Province land) {
        if (f == null || f.dead || land == null || !land.isLand() || f.cargo.isEmpty()) return 0;
        if (land != f.location && !f.location.neighbors.contains(land)) return 0;
        if (!g.allowed(f.owner, land)) return 0;
        int n = 0;
        for (Army a : new ArrayList<>(f.cargo)) {
            if (a.dead) continue;
            a.fleet = null;
            a.location = land;
            a.daysHere = 0;
            a.clearPath();
            n++;
        }
        f.cargo.clear();
        f.target = null;
        g.mapDirty = true;
        if (n > 0) g.landings++;
        return n;
    }

    // ---------------------------------------------------------------- daily simulation

    /** Moves fleets, resolves sea battles, updates blockades. */
    static void tick(Game g) {
        if (g.fleets.isEmpty() && g.blockaded.isEmpty()) return;
        move(g);
        battles(g);
        for (Fleet f : g.fleets) {
            if (f.dead) continue;
            // A fleet in a port that was lost puts to sea.
            if (f.location.isLand() && !canEnter(g, f.owner, f.location)) {
                for (Province q : f.location.neighbors) {
                    if (!q.isLand()) {
                        f.moveTo(q);
                        f.clearPath();
                        break;
                    }
                }
            }
            for (Army a : f.cargo) if (!a.dead) a.location = f.location;
        }
        g.fleets.removeIf(f -> f.dead);
        mergeAi(g);
        updateBlockades(g);
    }

    private static void move(Game g) {
        for (Fleet f : g.fleets) {
            if (f.dead) continue;
            f.daysHere++;
            if (f.path.isEmpty()) {
                f.progress = 0;
                continue;
            }
            Province next = f.path.peek();
            if (!canEnter(g, f.owner, next)) {
                f.clearPath();
                continue;
            }
            f.progress += 1;
            if (f.progress >= days(next)) {
                f.moveTo(next);
                f.path.poll();
                f.progress = 0;
            }
        }
    }

    private static void battles(Game g) {
        Map<Province, List<Fleet>> bySea = new LinkedHashMap<>();
        for (Fleet f : g.fleets) if (!f.dead && !f.location.isLand()) bySea.computeIfAbsent(f.location, k -> new ArrayList<>()).add(f);
        for (List<Fleet> list : bySea.values()) {
            if (list.size() < 2) continue;
            double[] dmg = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                for (int j = 0; j < list.size(); j++) {
                    if (i == j || !g.atWar(list.get(i).owner, list.get(j).owner)) continue;
                    dmg[i] += list.get(j).strength * 0.05 * (0.8 + 0.4 * g.rnd.nextDouble());
                }
            }
            for (int i = 0; i < list.size(); i++) {
                if (dmg[i] <= 0) continue;
                Fleet f = list.get(i);
                g.noteBattle(f.location);
                f.strength -= Math.max(1, (int) dmg[i]);
                if (f.strength < 100) sink(g, f);
            }
        }
    }

    /** A destroyed fleet takes its cargo down with it. */
    private static void sink(Game g, Fleet f) {
        f.dead = true;
        int lost = f.cargoMen();
        for (Army a : f.cargo) {
            a.dead = true;
            a.fleet = null;
        }
        f.cargo.clear();
        String where = f.location.isLand() ? f.location.name : I18n.tr("fleet.sea");
        if (lost > 0) g.report(mapgame.audio.Synth.Cue.BAD, I18n.tr("fleet.sunk.cargo", where, lost), f.location, f.owner);
        else g.report(mapgame.audio.Synth.Cue.BAD, I18n.tr("fleet.sunk", where), f.location, f.owner);
    }

    /** A coastal province is blockaded while an enemy fleet lies in an adjacent sea and no defending fleet is there. */
    private static void updateBlockades(Game g) {
        g.blockaded.clear();
        for (Fleet f : g.fleets) {
            if (f.dead || f.location.isLand()) continue;
            for (Province p : f.location.neighbors) {
                if (!p.isLand() || !p.coastal || p.controller == null || !g.atWar(f.owner, p.controller)) continue;
                boolean defended = false;
                for (Fleet d : g.fleets) {
                    if (!d.dead && d.location == f.location && g.friendly(p.controller, d.owner)) {
                        defended = true;
                        break;
                    }
                }
                if (!defended) g.blockaded.add(p);
            }
        }
    }

    /** AI fleets in the same place join up; the player's fleets stay as they are. */
    private static void mergeAi(Game g) {
        if (g.day % 5 != 0) return;
        Map<Long, Fleet> hosts = new HashMap<>();
        for (Fleet f : g.fleets) {
            if (f.dead || g.isHuman(f.owner) || !f.path.isEmpty() || !f.cargo.isEmpty()) continue;
            long key = ((long) f.location.id << 32) | f.owner.id;
            Fleet host = hosts.get(key);
            if (host == null) {
                hosts.put(key, f);
            } else if (host.cargo.isEmpty()) {
                host.strength += f.strength;
                f.dead = true;
            }
        }
        g.fleets.removeIf(f -> f.dead);
    }

    // ---------------------------------------------------------------- AI

    /** Builds a few fleets, hunts enemy fleets, blockades and ferries armies over the sea. */
    static void ai(Game g, Country c) {
        List<Fleet> mine = g.fleetsOf(c);
        boolean atWar = !g.enemiesOf(c).isEmpty();
        List<Province> ports = new ArrayList<>();
        for (Province p : c.provinces) if (p.controller == c && isPort(p)) ports.add(p);
        if (ports.isEmpty()) return;

        int cap = Math.min(4, ports.size() / 6);
        if (mine.size() < cap && (atWar || mine.isEmpty()) && c.gold > 250 && c.manpower > 1500 && g.rnd.nextDouble() < 0.15) {
            Fleet f = recruit(g, c, ports.get(g.rnd.nextInt(ports.size())));
            if (f != null) mine.add(f);
        }
        for (Fleet f : mine) {
            if (!f.dead) aiFleet(g, c, f, atWar, ports);
        }
    }

    private static void aiFleet(Game g, Country c, Fleet f, boolean atWar, List<Province> ports) {
        if (!f.cargo.isEmpty()) {
            aiInvade(g, c, f, ports);
            return;
        }
        if (!f.path.isEmpty()) return;
        if (!atWar) {
            if (!f.inPort()) {
                List<Province> home = path(g, c, f.location, Navy::isPort);
                if (home != null) f.setPath(home);
            }
            return;
        }
        if (f.inPort() && aiLoad(g, c, f)) return;
        List<Province> hunt = path(g, c, f.location, p -> !p.isLand() && (enemyFleetAt(g, c, p) || enemyCoast(g, c, p) != null));
        if (hunt != null) f.setPath(hunt);
    }

    /** Loads waiting armies and picks the enemy coast to land on. */
    private static boolean aiLoad(Game g, Country c, Fleet f) {
        boolean waiting = false;
        for (Army a : g.armies) {
            if (!a.dead && a.fleet == null && a.owner == c && a.location == f.location && a.path.isEmpty()) waiting = true;
        }
        if (!waiting) return false;
        List<Province> route = path(g, c, f.location, p -> !p.isLand() && enemyCoast(g, c, p) != null);
        if (route == null) return false;
        Province target = enemyCoast(g, c, route.get(route.size() - 1));
        if (embarkAll(g, f) == 0) return false;
        f.target = target;
        f.setPath(route);
        return true;
    }

    private static void aiInvade(Game g, Country c, Fleet f, List<Province> ports) {
        Province t = f.target;
        if (t != null && t.isLand() && g.allowed(c, t)) {
            if (f.location.neighbors.contains(t)) {
                disembark(g, f, t);
                return;
            }
            if (f.path.isEmpty()) {
                List<Province> route = path(g, c, f.location, p -> !p.isLand() && p.neighbors.contains(t));
                if (route != null) f.setPath(route);
                else f.target = null;
            }
            return;
        }
        // The war is over or the target changed: bring the army home.
        if (f.inPort() && f.location.owner == c) {
            disembark(g, f, f.location);
        } else if (f.path.isEmpty()) {
            List<Province> home = path(g, c, f.location, p -> isPort(p) && p.owner == c);
            if (home != null) f.setPath(home);
        }
    }

    private static boolean enemyFleetAt(Game g, Country c, Province sea) {
        for (Fleet o : g.fleets) if (!o.dead && o.location == sea && g.atWar(c, o.owner)) return true;
        return false;
    }

    /** An enemy coastal province next to the sea province (one the nation does not hold yet), or null. */
    private static Province enemyCoast(Game g, Country c, Province sea) {
        for (Province p : sea.neighbors) {
            if (p.isLand() && p.coastal && p.owner != null && g.atWar(c, p.owner) && p.controller != c) return p;
        }
        return null;
    }

    /** AI armies without a land route to the enemy walk to a port where one of their fleets waits. */
    static boolean requestTransport(Game g, Country c, Army a) {
        for (Fleet f : g.fleets) {
            if (f.dead || f.owner != c || !f.inPort() || !f.cargo.isEmpty() || !f.path.isEmpty() || f.location.controller != c) continue;
            if (f.location == a.location) return true;
            List<Province> path = g.findPath(c, a.location, p -> p == f.location);
            if (path != null) {
                a.setPath(path);
                return true;
            }
        }
        return false;
    }
}
