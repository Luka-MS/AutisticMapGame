package mapgame.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * A war fleet: sails over sea provinces and between friendly ports, fights enemy fleets, blockades coasts and
 * carries armies. Its strength counts hull and crews together.
 */
public final class Fleet {
    /** Armies a fleet can carry per point of strength. */
    public static final int CARRY_PER_STRENGTH = 4;

    public final int id;
    public final Country owner;
    /** A sea province, or a friendly coastal province (the fleet is then in port). */
    public Province location;
    public int strength;
    public final Deque<Province> path = new ArrayDeque<>();
    /** Days spent travelling towards path.peek(). */
    public double progress;
    public int daysHere;
    public boolean dead;
    /** Armies on board; they move with the fleet and cannot fight until they land. */
    public final List<Army> cargo = new ArrayList<>();
    /** Land province an invading AI fleet wants to land on, or null. */
    public Province target;

    public Fleet(int id, Country owner, Province location, int strength) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.strength = strength;
    }

    /** Men the fleet can carry. */
    public int capacity() {
        return strength * CARRY_PER_STRENGTH;
    }

    public int cargoMen() {
        int n = 0;
        for (Army a : cargo) if (!a.dead) n += a.strength;
        return n;
    }

    public boolean inPort() {
        return location.isLand();
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
