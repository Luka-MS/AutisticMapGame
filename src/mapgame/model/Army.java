package mapgame.model;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

public final class Army {
    public final int id;
    public final Country owner;
    public Province location;
    public int strength;
    public final Deque<Province> path = new ArrayDeque<>();
    /** Days spent travelling towards path.peek(). */
    public double progress;
    /** Days spent in the current province; occupation needs a few. */
    public int daysHere;
    public boolean dead;
    /** Army group this army belongs to, or null if it is under manual control. */
    public ArmyGroup group;
    /** The fleet this army is aboard, or null. Embarked armies take no part in land warfare. */
    public Fleet fleet;
    public ArmyType type = ArmyType.INFANTRY;
    /** Battle experience, 0 - {@link #MAX_EXPERIENCE}: veterans hit harder and take less damage. */
    public double experience;
    public static final double MAX_EXPERIENCE = 100;
    /** 0 - 100: falls in battle and recovers afterwards; a shaken army hits weaker and breaks when it reaches {@link #BREAK_MORALE}. */
    public double morale = 100;
    public static final double BREAK_MORALE = 20;

    private static final String[] GENERAL_FIRST = {"Aldric", "Berin", "Casimir", "Dorian", "Edmund", "Fabian", "Gregor", "Heron",
            "Ivor", "Jorin", "Konrad", "Leopold", "Matthias", "Nikolai", "Osric", "Pavel", "Radko", "Stefan", "Tiberio", "Ulric", "Viktor", "Wenzel"};
    private static final String[] GENERAL_LAST = {"Arvale", "Brandt", "Corvin", "Dalmar", "Eisen", "Falk", "Grau", "Hartmann",
            "Ilic", "Jessen", "Kovac", "Lorant", "Maren", "Novak", "Orlov", "Petrov", "Rask", "Sorel", "Thorn", "Varga"};

    /** Commander quality 0 - 4, earned through experience; 0 means no notable general. */
    public int generalSkill() {
        return (int) Math.min(4, experience / 25);
    }

    /** Name of the commander (stable for an army), or null while there is no notable general. */
    public String general() {
        if (generalSkill() == 0) return null;
        int h = id * 31 + owner.id * 17;
        return GENERAL_FIRST[Math.floorMod(h, GENERAL_FIRST.length)] + " " + GENERAL_LAST[Math.floorMod(h / 7, GENERAL_LAST.length)];
    }

    public Army(int id, Country owner, Province location, int strength) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.strength = strength;
    }

    public Army(int id, Country owner, Province location, int strength, ArmyType type) {
        this(id, owner, location, strength);
        this.type = type;
    }

    /** Damage dealt multiplier from experience, military research, the general and morale. */
    public double attackBonus() {
        return (1 + experience / 250) * (1 + 0.06 * owner.techLevel(TechField.MILITARY)) * (1 + 0.05 * generalSkill())
                * (0.7 + 0.3 * morale / 100);
    }

    /** Damage taken multiplier from experience (down to 75%) and military research. */
    public double toughness() {
        return (1 - experience / 400) * (1 - 0.04 * owner.techLevel(TechField.MILITARY));
    }

    /** New men join untrained and water down the experience. */
    public void addMen(int men) {
        if (men <= 0) return;
        experience = experience * strength / (strength + (double) men);
        strength += men;
    }

    /** Takes over all men of {@code other} (same type), averaging the experience by size. */
    public void absorb(Army other) {
        int total = strength + other.strength;
        if (total > 0) {
            experience = (experience * strength + other.experience * other.strength) / total;
            morale = (morale * strength + other.morale * other.strength) / total;
        }
        strength = total;
    }

    public void setPath(Collection<Province> newPath) {
        Province oldNext = path.peek();
        path.clear();
        path.addAll(newPath);
        if (path.peek() != oldNext) progress = 0;
    }

    public void clearPath() {
        path.clear();
        progress = 0;
    }

    public void moveTo(Province p) {
        location = p;
        daysHere = 0;
    }
}
