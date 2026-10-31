package mapgame.tools;

import java.util.ArrayList;
import java.util.List;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.sim.Game;

/**
 * Measures how the AI-only world develops: survivors, size of the biggest nation, wars and peace deals.
 * Usage: BalanceCheck [random|europe|world|all] [seeds] [years]
 */
public final class BalanceCheck {
    public static void main(String[] args) {
        String which = args.length > 0 ? args[0].toLowerCase() : "all";
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int years = args.length > 2 ? Integer.parseInt(args[2]) : 40;
        int[] marks = {5, 10, 20, years};

        for (MapKind kind : MapKind.values()) {
            if (!which.equals("all") && !which.equals(kind.name().toLowerCase())) continue;
            double[] alive = new double[marks.length], top = new double[marks.length], top3 = new double[marks.length];
            double[] changed = new double[marks.length], rump = new double[marks.length], strong = new double[marks.length];
            double[] indebted = new double[marks.length];
            long wars = 0, totalWars = 0, coalitions = 0, peaces = 0, eliminated = 0;
            int startCountries = 0;
            for (int s = 1; s <= seeds; s++) {
                Game g = Game.create(kind, 1000 + s);
                startCountries = g.world.countries.size();
                int landProvinces = 0;
                int[] startOwner = new int[g.world.provinces.size()];
                int[] startSize = new int[startCountries];
                for (Province p : g.world.provinces) {
                    if (!p.isLand() || p.owner == null) continue;
                    landProvinces++;
                    startOwner[p.id] = p.owner.id;
                    startSize[p.owner.id]++;
                }
                int mark = 0;
                for (int d = 1; d <= years * 360 && mark < marks.length; d++) {
                    g.tickDay();
                    if (d == marks[mark] * 360) {
                        List<Integer> sizes = new ArrayList<>();
                        int moved = 0, rumps = 0, stillStrong = 0, debt = 0;
                        for (Country c : g.aliveCountries()) {
                            sizes.add(c.provinces.size());
                            if (c.gold < 0) debt++;
                            if (c.provinces.size() <= 2) rumps++;
                            if (c.provinces.size() >= startSize[c.id] * 0.6) stillStrong++;
                        }
                        for (Province p : g.world.provinces) if (p.isLand() && p.owner != null && p.owner.id != startOwner[p.id]) moved++;
                        sizes.sort((a, b) -> b - a);
                        alive[mark] += g.aliveCountries().size();
                        top[mark] += 100.0 * sizes.get(0) / landProvinces;
                        top3[mark] += 100.0 * (sizes.get(0) + sizes.get(1) + sizes.get(2)) / landProvinces;
                        changed[mark] += 100.0 * moved / landProvinces;
                        rump[mark] += rumps;
                        strong[mark] += stillStrong;
                        indebted[mark] += debt;
                        mark++;
                    }
                }
                wars += g.warsDeclared;
                totalWars += g.totalWarsDeclared;
                coalitions += g.coalitionWars;
                peaces += g.peaceDeals;
                eliminated += g.eliminations;
            }
            System.out.printf("%s (%d nations, %d seeds, averages)%n", kind, startCountries, seeds);
            for (int i = 0; i < marks.length; i++) {
                System.out.printf("  year %2d: %5.1f alive, %4.1f with <=2 provinces, %4.1f keep >=60%% of start size, %4.1f in debt | biggest %4.1f%%, top 3 %4.1f%% | %4.1f%% of land changed owner%n",
                        marks[i], alive[i] / seeds, rump[i] / seeds, strong[i] / seeds, indebted[i] / seeds, top[i] / seeds, top3[i] / seeds, changed[i] / seeds);
            }
            System.out.printf("  per seed: %d wars (%d total, %d coalition), %d peace deals, %.1f eliminations%n",
                    wars / seeds, totalWars / seeds, coalitions / seeds, peaces / seeds, (double) eliminated / seeds);
        }
    }
}
