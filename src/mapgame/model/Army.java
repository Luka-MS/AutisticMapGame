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

    public Army(int id, Country owner, Province location, int strength) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.strength = strength;
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
