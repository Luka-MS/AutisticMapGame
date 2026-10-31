package mapgame.sim;

import java.util.ArrayList;
import java.util.List;
import mapgame.i18n.I18n;
import mapgame.model.Army;
import mapgame.model.Country;
import mapgame.model.Policy;
import mapgame.model.Province;

/**
 * Random events with decisions. Every month each nation has a small chance of an event; the player is asked
 * how to react (see {@link Game#decisions}), AI nations choose by themselves.
 */
final class GameEvents {
    /** Chance per nation and month. */
    private static final double CHANCE = 0.05;

    enum Type {
        HARVEST(3, 1),
        PLAGUE(1.5, 2),
        REVOLT(1.5, 2),
        TRADE(2, 2),
        REFORM(1, 2),
        DISASTER(1.5, 2),
        COUP(1, 2);

        final double weight;
        final int options;

        Type(double weight, int options) {
            this.weight = weight;
            this.options = options;
        }
    }

    private GameEvents() {
    }

    static void monthly(Game g, Country c) {
        if (g.rnd.nextDouble() >= CHANCE) return;
        List<Type> eligible = new ArrayList<>();
        double total = 0;
        for (Type t : Type.values()) {
            if (!eligible(g, c, t)) continue;
            eligible.add(t);
            total += t.weight;
        }
        double r = g.rnd.nextDouble() * total;
        Type type = eligible.get(eligible.size() - 1);
        for (Type t : eligible) {
            r -= t.weight;
            if (r < 0) {
                type = t;
                break;
            }
        }
        fire(g, c, type);
    }

    private static boolean eligible(Game g, Country c, Type t) {
        return switch (t) {
            case REVOLT -> c.provinces.size() >= 3;
            case TRADE -> c.provinces.size() >= 2 && c.gold >= 0;
            case COUP -> c.provinces.size() >= 6 && c.gold < 0;
            case DISASTER -> !c.provinces.isEmpty();
            default -> true;
        };
    }

    /** Gold amount that scales an event: one month of income, at least 10. */
    private static int unit(Game g, Country c) {
        return (int) Math.max(10, g.monthlyIncome(c));
    }

    /** Price of the first option (0 if it is free); the AI takes it only when it can pay. */
    private static int cost(Game g, Country c, Type t) {
        int u = unit(g, c);
        return switch (t) {
            case PLAGUE -> u / 2;
            case REVOLT -> u * 3 / 5;
            case REFORM -> u;
            case DISASTER -> u / 2;
            case COUP -> 150;
            default -> 0;
        };
    }

    static void fire(Game g, Country c, Type t) {
        int u = unit(g, c);
        Province where = t == Type.DISASTER ? c.provinces.get(g.rnd.nextInt(c.provinces.size())) : null;
        String place = where == null ? "" : where.name;
        int amount = t == Type.HARVEST ? u * 3 / 2 : cost(g, c, t);
        int option = 0;
        if (t.options > 1) {
            if (c == g.player) {
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < t.options; i++) labels.add(I18n.tr("event." + t.name() + ".opt" + i, c.displayName(), amount, place));
                option = g.decisions.choose(I18n.tr("event." + t.name() + ".title"),
                        I18n.tr("event." + t.name() + ".text", c.displayName(), amount, place), labels);
                option = Math.max(0, Math.min(t.options - 1, option));
            } else if (c.gold < amount) {
                option = t.options - 1;
            }
        }
        apply(g, c, t, option, amount, where);
        String title = I18n.tr("event." + t.name() + ".title");
        String choice = I18n.tr("event." + t.name() + ".opt" + option, c.displayName(), amount, place);
        g.report(I18n.tr("event.result", c.displayName(), title, choice), where, c);
    }

    private static void apply(Game g, Country c, Type t, int option, int amount, Province where) {
        switch (t) {
            case HARVEST -> c.gold += amount;
            case PLAGUE -> {
                if (option == 0) {
                    c.gold -= amount;
                    c.manpower = (int) (c.manpower * 0.95);
                } else {
                    c.manpower = (int) (c.manpower * 0.8);
                    shrinkArmies(g, c, 0.95);
                }
            }
            case REVOLT -> {
                if (option == 0) {
                    c.gold -= amount;
                } else {
                    c.taxes = Policy.Taxes.LOW;
                    c.policyChangedDay = g.day;
                }
            }
            case TRADE -> {
                if (option == 0) {
                    c.incomeBonus = 0.10;
                    c.incomeBonusUntil = g.day + 1800;
                }
            }
            case REFORM -> {
                if (option == 0) {
                    c.gold -= amount;
                    for (Army a : g.armies) if (!a.dead && a.owner == c) a.experience = Math.min(Army.MAX_EXPERIENCE, a.experience + 10);
                }
            }
            case DISASTER -> {
                if (option == 0) {
                    c.gold -= amount;
                    where.population = (int) (where.population * 0.95);
                } else {
                    where.population = (int) (where.population * 0.75);
                }
            }
            case COUP -> {
                if (option == 0) c.gold -= amount;
                else shrinkArmies(g, c, 0.75);
            }
        }
    }

    private static void shrinkArmies(Game g, Country c, double factor) {
        for (Army a : g.armies) if (!a.dead && a.owner == c) a.strength = (int) (a.strength * factor);
    }
}
