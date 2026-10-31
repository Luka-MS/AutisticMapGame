package mapgame.sim;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mapgame.model.Army;
import mapgame.model.ArmyType;
import mapgame.model.Building;
import mapgame.model.Country;
import mapgame.model.Policy;
import mapgame.model.Province;
import mapgame.model.Terrain;

/** Simple computer opponent: recruits, attacks, makes peace and occasionally declares war. */
final class Ai {
    private Ai() {
    }

    static void think(Game g, Country c) {
        if (c.gold < 0) controlDebt(g, c);
        makePeace(g, c);
        if (!c.alive) return;
        diplomacy(g, c);
        policies(g, c);
        build(g, c);
        joinCoalition(g, c);
        List<Country> foes = g.enemiesOf(c);
        if (!foes.isEmpty()) attack(g, c);
        Navy.ai(g, c);
        recruit(g, c, !foes.isEmpty());
        if (foes.isEmpty()) maybeDeclareWar(g, c);
    }

    /** A nation in debt cannot pay its soldiers: it sends some of them home until the budget is balanced. */
    private static void controlDebt(Game g, Country c) {
        if (g.monthlyUpkeep(c) > g.monthlyIncome(c)) g.demobilize(c, 0.10);
    }

    /** Taxes up when broke, conscription up when the army cannot be filled during a war. */
    private static void policies(Game g, Country c) {
        if (g.day % 30 != 0) return;
        boolean atWar = !g.enemiesOf(c).isEmpty();
        Policy.Taxes taxes = c.taxes;
        Policy.Conscription draft = c.conscription;
        if (c.gold < 0) taxes = Policy.Taxes.HIGH;
        else if (c.gold > 400) taxes = Policy.Taxes.NORMAL;
        if (atWar && c.manpower < 3000) draft = Policy.Conscription.HEAVY;
        else if (!atWar && c.manpower > 6000) draft = Policy.Conscription.NORMAL;
        g.setPolicies(c, taxes, draft);
    }

    /** Once a month a nation with spare gold develops its richest province, or fortifies a border province at war. */
    private static void build(Game g, Country c) {
        if (g.day % 30 != 0 || c.gold < 250 || c.provinces.isEmpty() || g.rnd.nextDouble() > 0.6) return;
        boolean atWar = !g.enemiesOf(c).isEmpty();
        Province best = null;
        Building what = null;
        for (Province p : c.provinces) {
            if (p.controller != c || p.building != null) continue;
            Building b = null;
            if (atWar && p.level(Building.FORT) < 2 && facesEnemy(g, c, p)) b = Building.FORT;
            else if (p.level(Building.MARKET) < 2) b = Building.MARKET;
            else if (p.level(Building.BARRACKS) < 1) b = Building.BARRACKS;
            if (b == null) continue;
            if (best == null || b == Building.FORT && what != Building.FORT || b == what && p.population > best.population) {
                best = p;
                what = b;
            }
        }
        if (best != null) g.build(c, best, what);
    }

    private static boolean facesEnemy(Game g, Country c, Province p) {
        for (Province q : p.neighbors) if (q.owner != null && g.atWar(c, q.owner)) return true;
        return false;
    }

    /** Non-aggression pacts with neighbours, trade agreements, and guarantees for small neighbours of a hegemon. */
    private static void extraDiplomacy(Game g, Country c, Country hegemon) {
        if (g.rnd.nextDouble() < 0.03 && g.pactsOf(c) < 1) {
            List<Country> options = new ArrayList<>();
            for (Country o : g.world.countries) {
                if (o.alive && o != c && !g.isHuman(o) && !g.atWar(c, o) && !g.allied(c, o) && !g.hasPact(c, o) && g.bordersCountry(c, o)) options.add(o);
            }
            if (!options.isEmpty()) g.proposePact(c, options.get(g.rnd.nextInt(options.size())));
        }
        if (g.rnd.nextDouble() < 0.15 && g.tradesOf(c) < Game.MAX_TRADES) {
            List<Country> options = new ArrayList<>();
            for (Country o : g.world.countries) {
                if (o.alive && o != c && !g.isHuman(o) && !g.atWar(c, o) && !g.hasTrade(c, o) && g.tradesOf(o) < Game.MAX_TRADES) options.add(o);
            }
            if (!options.isEmpty()) g.makeTrade(c, options.get(g.rnd.nextInt(options.size())));
        }
        if (hegemon != null && hegemon != c && g.rnd.nextDouble() < 0.05) {
            List<Country> options = new ArrayList<>();
            for (Country o : g.world.countries) {
                if (o.alive && o != c && o != hegemon && !g.guarantees(c, o) && g.strength(o) < g.strength(c) && g.bordersCountry(o, hegemon)) options.add(o);
            }
            if (!options.isEmpty()) g.guarantee(c, options.get(g.rnd.nextInt(options.size())));
        }
    }

    /** Once a month: drop an alliance with a hegemon, and look for a new partner against a shared threat. */
    private static void diplomacy(Game g, Country c) {
        if (g.day % 30 != 0) return;
        Country h = g.hegemon();
        extraDiplomacy(g, c, h);
        for (Country ally : g.alliesOf(c)) {
            if (ally == h && g.rnd.nextDouble() < 0.5) g.breakAlliance(c, ally);
        }
        if (g.alliesOf(c).size() >= Game.MAX_ALLIES || g.rnd.nextDouble() > 0.2) return;
        List<Country> partners = new ArrayList<>();
        for (Country o : g.world.countries) {
            if (!o.alive || o == c || o == h || g.isHuman(o) || g.allied(c, o) || g.atWar(c, o)) continue;
            if (g.alliesOf(o).size() >= Game.MAX_ALLIES || o.provinces.size() < 3) continue;
            if (g.sharedInterest(c, o) || g.bordersCountry(c, o) && g.alliesOf(c).size() < 2 && g.alliesOf(o).size() < 2
                    && g.rnd.nextDouble() < 0.15) {
                partners.add(o);
            }
        }
        if (!partners.isEmpty()) g.makeAlliance(c, partners.get(g.rnd.nextInt(partners.size())));
    }

    /** Neighbours of an over-mighty nation gang up on it; this keeps the strongest AI from snowballing. */
    private static void joinCoalition(Game g, Country c) {
        if (g.aggression == 0 || g.day < 90 || g.day % 30 != 0) return;
        Country h = g.hegemon();
        if (h == null || h == c || g.atWar(c, h) || g.strength(c) < 2000 || g.rnd.nextDouble() > 0.35) return;
        for (Province p : c.provinces) {
            for (Province q : p.neighbors) {
                if (q.owner == h && g.declareWar(c, h)) {
                    g.coalitionWars++;
                    return;
                }
            }
        }
    }

    private static void makePeace(Game g, Country c) {
        for (Country f : g.enemiesOf(c)) {
            int duration = g.day - g.warStart(c, f);
            // War goal: the AI fights for a limited share of the enemy's land and settles once it holds that.
            int goal = Math.max(1, (int) Math.ceil(f.provinces.size() * Game.PEACE_SHARE * 0.6));
            if (g.warGoal(c, f) == Game.WarGoal.TRIBUTE) goal = 1;
            double chance = g.warScore(c, f) < 0 ? 0.06 : (duration > 120 ? 0.03 : 0);
            if (g.warScore(c, f) >= goal) chance = Math.max(chance, 0.15);
            if (g.rnd.nextDouble() < chance) {
                g.makePeace(c, f);
                if (!c.alive) return;
            }
        }
    }

    private static void attack(Game g, Country c) {
        Map<Province, Integer> enemyAt = g.enemyStrengthByProvince(c);
        for (Army a : g.armiesOf(c)) {
            if (a.dead || a.fleet != null || !a.path.isEmpty() || g.isEngaged(a)) continue;
            int str = a.strength;
            List<Province> path = g.findPath(c, a.location, p -> {
                if (p.owner != null && g.atWar(c, p.owner) && p.controller != c) return true;
                Integer e = enemyAt.get(p);
                return e != null && str >= e * 1.2;
            });
            if (path != null) a.setPath(path);
            else if (g.fleetsOf(c).size() > 0) Navy.requestTransport(g, c, a);
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
        if (pool.isEmpty()) return;
        Province site = pool.get(g.rnd.nextInt(pool.size()));
        double r = g.rnd.nextDouble();
        ArmyType type = ArmyType.INFANTRY;
        // A mix of troops: horsemen for open land, guns when at war and rich enough.
        if (atWar && c.gold > 300 && r < 0.12) type = ArmyType.ARTILLERY;
        else if (r < 0.30 && (site.terrain == Terrain.PLAINS || site.terrain == Terrain.DESERT)) type = ArmyType.CAVALRY;
        if (g.recruit(c, site, type) == null && type != ArmyType.INFANTRY) g.recruit(c, site);
    }

    private static void maybeDeclareWar(Game g, Country c) {
        if (g.day < 90 || g.day < c.truceUntil || g.day % 30 != 0 || g.rnd.nextDouble() > 0.10 * g.aggression / 50.0) return;
        long mine = g.strength(c);
        if (mine < 4000) return;
        Set<Country> neighbors = new LinkedHashSet<>();
        for (Province p : c.provinces) {
            for (Province q : p.neighbors) {
                if (q.owner != null && q.owner != c && q.owner.alive && !g.allied(c, q.owner)) neighbors.add(q.owner);
            }
        }
        List<Country> targets = new ArrayList<>();
        for (Country n : neighbors) if (g.strength(n) * 1.5 < mine && !g.hasPact(c, n)) targets.add(n);
        if (targets.isEmpty()) return;
        Country target = targets.get(g.rnd.nextInt(targets.size()));
        // Rarely a strong nation goes for the kill against a much weaker neighbour.
        boolean total = c.provinces.size() >= 6 && g.strength(target) * 3 < mine && g.rnd.nextDouble() < 0.08;
        if (g.declareWar(c, target, total) && !total && g.rnd.nextDouble() < 0.3) g.setWarGoal(c, target, Game.WarGoal.TRIBUTE);
    }
}
