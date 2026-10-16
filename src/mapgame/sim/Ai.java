package mapgame.sim;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mapgame.model.Army;
import mapgame.model.Country;
import mapgame.model.Province;

/** Simple computer opponent: recruits, attacks, makes peace and occasionally declares war. */
final class Ai {
    private Ai() {
    }

    static void think(Game g, Country c) {
        makePeace(g, c);
        if (!c.alive) return;
        List<Country> foes = g.enemiesOf(c);
        if (!foes.isEmpty()) attack(g, c);
        recruit(g, c, !foes.isEmpty());
        if (foes.isEmpty()) maybeDeclareWar(g, c);
    }

    private static void makePeace(Game g, Country c) {
        for (Country f : g.enemiesOf(c)) {
            int duration = g.day - g.warStart(c, f);
            double chance = g.warScore(c, f) < 0 ? 0.06 : (duration > 120 ? 0.03 : 0);
            if (g.rnd.nextDouble() < chance) {
                g.makePeace(c, f);
                if (!c.alive) return;
            }
        }
    }

    private static void attack(Game g, Country c) {
        Map<Province, Integer> enemyAt = g.enemyStrengthByProvince(c);
        for (Army a : g.armiesOf(c)) {
            if (a.dead || !a.path.isEmpty() || g.isEngaged(a)) continue;
            int str = a.strength;
            List<Province> path = g.findPath(c, a.location, p -> {
                if (p.owner != null && g.atWar(c, p.owner) && p.controller != c) return true;
                Integer e = enemyAt.get(p);
                return e != null && str >= e * 1.2;
            });
            if (path != null) a.setPath(path);
        }
    }

    private static void recruit(Game g, Country c, boolean atWar) {
        long cap = c.provinces.size() * 1200L + 3000;
        if (g.strength(c) >= cap || c.gold < 150 || c.manpower < Game.RECRUIT_MEN) return;
        List<Province> frontline = new ArrayList<>();
        List<Province> all = new ArrayList<>();
        for (Province p : c.provinces) {
            if (p.controller != c) continue;
            all.add(p);
            if (atWar) {
                for (Province q : p.neighbors) {
                    if (q.owner != null && g.atWar(c, q.owner)) {
                        frontline.add(p);
                        break;
                    }
                }
            }
        }
        List<Province> pool = frontline.isEmpty() ? all : frontline;
        if (!pool.isEmpty()) g.recruit(c, pool.get(g.rnd.nextInt(pool.size())));
    }

    private static void maybeDeclareWar(Game g, Country c) {
        if (g.day < 90 || g.day < c.truceUntil || g.day % 30 != 0 || g.rnd.nextDouble() > 0.10) return;
        long mine = g.strength(c);
        if (mine < 4000) return;
        Set<Country> neighbors = new LinkedHashSet<>();
        for (Province p : c.provinces) {
            for (Province q : p.neighbors) {
                if (q.owner != null && q.owner != c && q.owner.alive) neighbors.add(q.owner);
            }
        }
        List<Country> targets = new ArrayList<>();
        for (Country n : neighbors) if (g.strength(n) * 1.5 < mine) targets.add(n);
        if (!targets.isEmpty()) g.declareWar(c, targets.get(g.rnd.nextInt(targets.size())));
    }
}
