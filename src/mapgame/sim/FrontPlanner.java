package mapgame.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.Country;
import mapgame.model.Province;

/**
 * Drives an army group: finds the front against its target, splits armies so that the
 * front is covered, spreads the men evenly over the front provinces and issues move orders.
 */
final class FrontPlanner {
    /** Rough number of men needed to hold or attack one province. */
    static final int MIN_PER_SLOT = 800;
    private static final double DISTANCE_WEIGHT = 1 / 600.0;

    private FrontPlanner() {
    }

    static void update(Game g, ArmyGroup grp) {
        grp.armies.removeIf(a -> a.dead);
        grp.slots.keySet().retainAll(grp.armies);
        grp.front = new ArrayList<>();
        Country me = grp.owner, foe = grp.target;
        if (!me.alive) return;
        if (grp.armies.isEmpty()) {
            grp.status = "No armies assigned. Select an army and press 'Add selected army'.";
            return;
        }
        if (foe == null) {
            grp.status = "No front selected. Choose an enemy nation.";
            return;
        }
        if (!foe.alive || !g.atWar(me, foe)) {
            grp.slots.clear();
            grp.status = "Not at war with " + foe.name + ". Armies are waiting.";
            return;
        }

        boolean attack = grp.stance == ArmyGroup.Stance.ATTACK;
        List<Province> candidates = attack ? enemyContact(g, me, foe) : ownContact(g, me, foe);
        if (candidates.isEmpty()) {
            noContact(g, grp, attack);
            return;
        }

        int total = grp.strength();
        int wanted = Math.max(1, Math.min(candidates.size(), total / MIN_PER_SLOT));
        double[] centre = centre(grp);
        Map<Province, Integer> foeAt = new HashMap<>();
        for (Army a : g.armies) if (!a.dead && a.owner == foe) foeAt.merge(a.location, a.strength, Integer::sum);

        ToDoubleFunction<Province> cost = attack
                ? p -> foeAt.getOrDefault(p, 0) / 1000.0 - p.population / 50000.0 + dist(p, centre) / 400.0
                : p -> -(threat(p, foe, foeAt)) / 1000.0 - adjacency(p, foe) + dist(p, centre) / 400.0;
        candidates.sort(Comparator.comparingDouble(cost));
        List<Province> front = new ArrayList<>(candidates.subList(0, wanted));
        grp.front = front;

        // Cover the front: split the biggest idle armies until there is one per front province.
        for (int guard = 0; guard < 64 && grp.armies.size() < wanted; guard++) {
            Army biggest = null;
            for (Army a : grp.armies) {
                if (g.canSplit(a) && (biggest == null || a.strength > biggest.strength)) biggest = a;
            }
            if (biggest == null || g.split(biggest) == null) break;
        }

        assign(g, grp, front, total);
        order(g, grp);
        grp.status = String.format("Front vs %s: %d provinces, %d armies, %,d men (%s)",
                foe.name, front.size(), grp.armies.size(), total, attack ? "attacking" : "defending");
    }

    /** Foe provinces we are touching (attack targets). */
    private static List<Province> enemyContact(Game g, Country me, Country foe) {
        List<Province> out = new ArrayList<>();
        for (Province q : g.world.provinces) {
            if (!q.isLand() || q.controller != foe) continue;
            for (Province p : q.neighbors) {
                if (p.isLand() && p.controller == me) {
                    out.add(q);
                    break;
                }
            }
        }
        return out;
    }

    /** Our provinces touching the foe (defence line). */
    private static List<Province> ownContact(Game g, Country me, Country foe) {
        List<Province> out = new ArrayList<>();
        for (Province p : g.world.provinces) {
            if (!p.isLand() || p.controller != me) continue;
            for (Province q : p.neighbors) {
                if (q.isLand() && q.controller == foe) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    /** No shared border (e.g. across the sea): attackers advance towards the nearest enemy land. */
    private static void noContact(Game g, ArmyGroup grp, boolean attack) {
        grp.slots.clear();
        if (!attack) {
            grp.status = "No contact with " + grp.target.name + ". Holding position.";
            return;
        }
        Country me = grp.owner, foe = grp.target;
        int moving = 0;
        for (Army a : grp.armies) {
            if (g.isEngaged(a) || !a.path.isEmpty()) {
                if (!a.path.isEmpty()) moving++;
                continue;
            }
            List<Province> path = g.findPath(me, a.location, p -> p.controller == foe);
            if (path != null) {
                a.setPath(path);
                moving++;
            }
        }
        grp.status = moving > 0 ? "Advancing towards " + foe.name + "." : "Cannot reach " + foe.name + " by land.";
    }

    private static void assign(Game g, ArmyGroup grp, List<Province> front, int total) {
        double target = Math.max(1, (double) total / front.size());
        Map<Province, Double> load = new HashMap<>();
        for (Province p : front) load.put(p, 0.0);
        List<Army> sorted = new ArrayList<>(grp.armies);
        sorted.sort((a, b) -> b.strength - a.strength);

        // Keep earlier assignments while they are still on the front and not overloaded (avoids shuffling).
        List<Army> unassigned = new ArrayList<>();
        Map<Army, Province> kept = new HashMap<>();
        for (Army a : sorted) {
            Province prev = grp.slots.get(a);
            if (prev != null && load.containsKey(prev) && (load.get(prev) == 0 || load.get(prev) + a.strength <= target * 1.25)) {
                load.merge(prev, (double) a.strength, Double::sum);
                kept.put(a, prev);
            } else {
                unassigned.add(a);
            }
        }
        grp.slots.clear();
        grp.slots.putAll(kept);
        for (Army a : unassigned) {
            Province best = null;
            double bestScore = Double.MAX_VALUE;
            for (Province p : front) {
                double s = load.get(p) / target + Math.hypot(p.x - a.location.x, p.y - a.location.y) * DISTANCE_WEIGHT;
                if (s < bestScore) {
                    bestScore = s;
                    best = p;
                }
            }
            load.merge(best, (double) a.strength, Double::sum);
            grp.slots.put(a, best);
        }
    }

    private static void order(Game g, ArmyGroup grp) {
        for (Army a : new ArrayList<>(grp.armies)) {
            Province slot = grp.slots.get(a);
            if (slot == null || a.dead || g.isEngaged(a)) continue;
            Province dest = null;
            for (Province p : a.path) dest = p;
            if (a.location == slot) {
                if (!a.path.isEmpty()) a.clearPath();
            } else if (dest != slot) {
                if (!g.moveArmy(a, slot)) grp.slots.remove(a);
            }
        }
    }

    private static double[] centre(ArmyGroup grp) {
        double x = 0, y = 0;
        for (Army a : grp.armies) {
            x += a.location.x;
            y += a.location.y;
        }
        int n = Math.max(1, grp.armies.size());
        return new double[] {x / n, y / n};
    }

    private static double dist(Province p, double[] c) {
        return Math.hypot(p.x - c[0], p.y - c[1]);
    }

    private static int threat(Province p, Country foe, Map<Province, Integer> foeAt) {
        int t = 0;
        for (Province q : p.neighbors) if (q.controller == foe) t += foeAt.getOrDefault(q, 0);
        return t;
    }

    private static int adjacency(Province p, Country foe) {
        int n = 0;
        for (Province q : p.neighbors) if (q.isLand() && q.controller == foe) n++;
        return n;
    }
}
