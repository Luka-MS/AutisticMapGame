package mapgame.sim;

import mapgame.i18n.I18n;

/** Game difficulty: scales the income of AI nations and of the player. */
public enum Difficulty {
    EASY(0.85, 1.10), NORMAL(1.0, 1.0), HARD(1.2, 0.95);

    /** Income multiplier for AI nations and for the player's nation. */
    public final double ai, player;

    Difficulty(double ai, double player) {
        this.ai = ai;
        this.player = player;
    }

    public String label() {
        return I18n.tr("difficulty." + name());
    }
}
