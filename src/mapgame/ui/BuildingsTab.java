package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import mapgame.i18n.I18n;
import mapgame.model.Building;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Develop the selected province: markets, barracks, forts and roads. */
final class BuildingsTab implements NationTab {
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private final JLabel title = new JLabel();
    private final JTextArea status = new JTextArea(5, 24);
    private final JLabel[] labels = new JLabel[Building.values().length];
    private final JLabel[] effects = new JLabel[Building.values().length];
    private final JButton[] buttons = new JButton[Building.values().length];
    private Game game;
    private Province province;

    BuildingsTab() {
        JPanel rows = new JPanel(new GridLayout(Building.values().length, 1, 0, 6));
        for (Building b : Building.values()) {
            int i = b.ordinal();
            labels[i] = new JLabel();
            effects[i] = new JLabel();
            buttons[i] = new JButton();
            buttons[i].setFocusable(false);
            buttons[i].addActionListener(e -> {
                if (game != null && province != null && game.player != null && !game.build(game.player, province, b)) {
                    game.log(I18n.tr("building.cannot"));
                }
            });
            JPanel left = new JPanel(new GridLayout(2, 1));
            left.add(labels[i]);
            left.add(effects[i]);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.add(left, BorderLayout.CENTER);
            row.add(buttons[i], BorderLayout.EAST);
            rows.add(row);
        }
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(title, BorderLayout.NORTH);
        root.add(rows, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
    }

    @Override
    public String titleKey() {
        return "tab.buildings";
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public void applyLanguage() {
        for (Building b : Building.values()) effects[b.ordinal()].setText(I18n.tr("building.effect." + b.name()));
    }

    @Override
    public void refresh(Game g, Province p) {
        game = g;
        province = p;
        Country me = g.player;
        boolean own = me != null && p != null && p.isLand() && p.owner == me && p.controller == me;
        title.setText(p == null || !p.isLand() ? I18n.tr("building.select") : p.name);
        StringBuilder sb = new StringBuilder();
        if (p != null && p.isLand()) {
            if (p.building != null) {
                sb.append(I18n.tr("building.progress", p.building.label(), Game.formatDate(p.buildDone))).append('\n');
            }
            if (p.unrest > 0) sb.append(I18n.tr("info.unrest", (int) p.unrest)).append('\n');
        }
        status.setText(sb.toString());
        for (Building b : Building.values()) {
            int i = b.ordinal();
            int level = p == null ? 0 : p.level(b);
            labels[i].setText(I18n.tr("building.level", b.label(), level, b.maxLevel));
            int cost = g.buildCost(p, b);
            buttons[i].setText(cost < 0 ? I18n.tr("building.none") : I18n.tr("building.build", cost));
            buttons[i].setEnabled(own && cost >= 0 && me.gold >= cost);
        }
    }
}
