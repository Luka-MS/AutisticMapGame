package mapgame.sim;

import java.util.ArrayList;
import java.util.List;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.Province;

/**
 * Monthly history of every nation: land, people, army, navy, gold and income. The numbers feed the charts in the
 * nation window and are part of a save.
 */
public final class Stats {
    public enum Metric {
        PROVINCES, POPULATION, ARMY, NAVY, GOLD, INCOME;

        public String label() {
            return I18n.tr("stats.metric." + name());
        }
    }

    private static final int METRICS = Metric.values().length;

    private final int countries;
    private final List<Integer> days = new ArrayList<>();
    /** One entry per sample: {@code countries * METRICS} values, nation-major. */
    private final List<int[]> samples = new ArrayList<>();

    public Stats(int countries) {
        this.countries = countries;
    }

    public int size() {
        return days.size();
    }

    public int day(int sample) {
        return days.get(sample);
    }

    public int value(int sample, Country c, Metric m) {
        return samples.get(sample)[c.id * METRICS + m.ordinal()];
    }

    /** The whole history of one metric of one nation. */
    public int[] series(Country c, Metric m) {
        int[] out = new int[size()];
        for (int i = 0; i < out.length; i++) out[i] = value(i, c, m);
        return out;
    }

    /** Takes a snapshot of every nation (dead nations count as zero). */
    void record(Game g) {
        int[] s = new int[countries * METRICS];
        for (Country c : g.world.countries) {
            if (!c.alive || c.id >= countries) continue;
            long pop = 0;
            for (Province p : c.provinces) pop += p.population;
            int base = c.id * METRICS;
            s[base + Metric.PROVINCES.ordinal()] = c.provinces.size();
            s[base + Metric.POPULATION.ordinal()] = (int) (pop / 1000);
            s[base + Metric.ARMY.ordinal()] = (int) Math.min(Integer.MAX_VALUE, g.strength(c));
            s[base + Metric.NAVY.ordinal()] = (int) Math.min(Integer.MAX_VALUE, g.navalStrength(c));
            s[base + Metric.GOLD.ordinal()] = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, c.gold));
            s[base + Metric.INCOME.ordinal()] = (int) g.monthlyIncome(c);
        }
        days.add(g.day);
        samples.add(s);
    }

    void clear() {
        days.clear();
        samples.clear();
    }

    // ---------------------------------------------------------------- saving

    void write(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(size());
        for (int i = 0; i < size(); i++) {
            out.writeInt(days.get(i));
            for (int v : samples.get(i)) out.writeInt(v);
        }
    }

    void read(java.io.DataInputStream in) throws java.io.IOException {
        clear();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            days.add(in.readInt());
            int[] s = new int[countries * METRICS];
            for (int k = 0; k < s.length; k++) s[k] = in.readInt();
            samples.add(s);
        }
    }
}
