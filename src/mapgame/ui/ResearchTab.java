package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.model.TechField;
import mapgame.sim.Game;

/** Pick what the nation researches; three fields with five levels each. */
final class ResearchTab implements NationTab {
    private final JPanel root = new JPanel(new BorderLayout(0, 10));
    private final JLabel rate = new JLabel();
    private final JLabel[] labels = new JLabel[TechField.values().length];
    private final JLabel[] effects = new JLabel[TechField.values().length];
    private final JButton[] buttons = new JButton[TechField.values().length];
    private final JProgressBar bar = new JProgressBar(0, 100);
    private Game game;

    ResearchTab() {
        JPanel rows = new JPanel(new GridLayout(TechField.values().length, 1, 0, 6));
        for (TechField f : TechField.values()) {
            int i = f.ordinal();
            labels[i] = new JLabel();
            effects[i] = new JLabel();
            buttons[i] = new JButton();
            buttons[i].setFocusable(false);
            buttons[i].addActionListener(e -> {
                if (game != null && game.player != null) game.startResearch(game.player, f);
            });
            JPanel left = new JPanel(new GridLayout(2, 1));
            left.add(labels[i]);
            left.add(effects[i]);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.add(left, BorderLayout.CENTER);
            row.add(buttons[i], BorderLayout.EAST);
            rows.add(row);
        }
        bar.setStringPainted(true);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(rate, BorderLayout.NORTH);
        root.add(rows, BorderLayout.CENTER);
        root.add(bar, BorderLayout.SOUTH);
    }

    @Override
    public String titleKey() {
        return "tab.research";
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public void applyLanguage() {
        for (TechField f : TechField.values()) effects[f.ordinal()].setText(I18n.tr("tech.effect." + f.name()));
    }

    @Override
    public void refresh(Game g, Province p) {
        game = g;
        Country me = g.player;
        if (me == null) return;
        rate.setText(I18n.tr("tech.rate", g.researchRate(me)));
        for (TechField f : TechField.values()) {
            int i = f.ordinal();
            int level = me.techLevel(f);
            labels[i].setText(I18n.tr("tech.level", f.label(), level, TechField.MAX_LEVEL));
            boolean active = me.researching == f;
            buttons[i].setText(level >= TechField.MAX_LEVEL ? I18n.tr("tech.max")
                    : I18n.tr(active ? "tech.active" : "tech.start", g.researchCost(me, f)));
            buttons[i].setEnabled(level < TechField.MAX_LEVEL && !active);
        }
        if (me.researching == null) {
            bar.setValue(0);
            bar.setString(I18n.tr("tech.idle"));
        } else {
            int cost = g.researchCost(me, me.researching);
            bar.setValue((int) (100 * me.researchProgress / cost));
            bar.setString(I18n.tr("tech.progress", me.researching.label(), (int) me.researchProgress, cost));
        }
    }
}
