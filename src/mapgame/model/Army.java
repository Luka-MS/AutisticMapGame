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
    public ArmyType type = ArmyType.INFANTRY;
    /** Battle experience, 0 - {@link #MAX_EXPERIENCE}: veterans hit harder and take less damage. */
    public double experience;
    public static final double MAX_EXPERIENCE = 100;

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

    /** Damage dealt multiplier from experience (up to +40%). */
    public double attackBonus() {
        return 1 + experience / 250;
    }

    /** Damage taken multiplier from experience (down to 75%). */
    public double toughness() {
        return 1 - experience / 400;
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
        if (total > 0) experience = (experience * strength + other.experience * other.strength) / total;
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
