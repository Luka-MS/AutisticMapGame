package mapgame.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A set of armies that is automatically spread over the front against one enemy nation. */
public final class ArmyGroup {
    public enum Stance {
        ATTACK("Attack (push into enemy provinces)"), DEFEND("Defend (hold our border provinces)");

        public final String label;

        Stance(String label) {
            this.label = label;
        }
    }

    public final int id;
    public final Country owner;
    public String name;
    /** Enemy this group fights; null until the player picks a front. */
    public Country target;
    public Stance stance = Stance.ATTACK;
    public final List<Army> armies = new ArrayList<>();
    /** Province each army was last assigned to. */
    public final Map<Army, Province> slots = new HashMap<>();
    /** Current front line provinces (for display). */
    public List<Province> front = new ArrayList<>();
    public String status = "No front selected.";

    public ArmyGroup(int id, Country owner) {
        this.id = id;
        this.owner = owner;
        this.name = "Army Group " + id;
    }

    public int strength() {
        int s = 0;
        for (Army a : armies) if (!a.dead) s += a.strength;
        return s;
    }

    @Override
    public String toString() {
        return name;
    }
}
