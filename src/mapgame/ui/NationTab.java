package mapgame.ui;

import javax.swing.JComponent;
import mapgame.model.Province;
import mapgame.sim.Game;

/** One page of the {@link NationDialog}. */
interface NationTab {
    /** I18n key of the tab title. */
    String titleKey();

    JComponent component();

    /** Brings the page up to date; {@code selected} is the province currently selected on the map (may be null). */
    void refresh(Game game, Province selected);

    /** Called when the language changed. */
    void applyLanguage();
}
