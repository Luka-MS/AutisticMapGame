package mapgame.model;

import mapgame.i18n.I18n;

/** National policies: tax level and conscription. Each trades gold against manpower. */
public final class Policy {
    private Policy() {
    }

    public enum Taxes {
        LOW(0.8, 1.1), NORMAL(1.0, 1.0), HIGH(1.25, 0.85);

        public final double gold, manpower;

        Taxes(double gold, double manpower) {
            this.gold = gold;
            this.manpower = manpower;
        }

        public String label() {
            return I18n.tr("policy.taxes." + name());
        }
    }

    public enum Conscription {
        LIGHT(1.05, 0.7), NORMAL(1.0, 1.0), HEAVY(0.9, 1.4);

        public final double gold, manpower;

        Conscription(double gold, double manpower) {
            this.gold = gold;
            this.manpower = manpower;
        }

        public String label() {
            return I18n.tr("policy.conscription." + name());
        }
    }

    /** Days a nation must wait before it may change its policies again. */
    public static final int CHANGE_DELAY = 180;
}
