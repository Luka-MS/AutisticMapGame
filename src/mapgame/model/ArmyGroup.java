package mapgame.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mapgame.i18n.I18n;

/** A set of armies that is automatically spread over the front against one enemy nation. */
public final class ArmyGroup {
    public enum Stance {
        ATTACK, DEFEND;

        public String label() {
            return I18n.tr("stance." + name());
        }
    }

    public final int id;
    public final Country owner;
    /** Null while the group has the default name, which follows the language setting. */
    public String name;
    /** Enemy this group fights; null until the player picks a front. */
    public Country target;
    public Stance stance = Stance.ATTACK;
    public final List<Army> armies = new ArrayList<>();
    /** Province each army was last assigned to. */
    public final Map<Army, Province> slots = new HashMap<>();
    /** Current front line provinces (for display). */
    public List<Province> front = new ArrayList<>();
    public String status = "";

    public ArmyGroup(int id, Country owner) {
        this.id = id;
        this.owner = owner;
    }

    public int strength() {
        int s = 0;
        for (Army a : armies) if (!a.dead) s += a.strength;
        return s;
    }

    public String displayName() {
        return name != null ? name : I18n.tr("group.default", id);
    }

    @Override
    public String toString() {
        return displayName();
    }
}
