package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Treaties with the nation owning the selected province: alliance, pact, guarantee, trade, and the war goal. */
final class DiplomacyTab implements NationTab {
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private final JLabel title = new JLabel(), goalLabel = new JLabel();
    private final JTextArea status = new JTextArea(6, 24);
    private final JButton alliance = new JButton(), pact = new JButton(), guarantee = new JButton(), trade = new JButton();
    private final JComboBox<Game.WarGoal> goalBox = new JComboBox<>(Game.WarGoal.values());
    private Game game;
    private Country other;

    DiplomacyTab() {
        for (JButton b : new JButton[] {alliance, pact, guarantee, trade}) b.setFocusable(false);
        alliance.addActionListener(e -> act(() -> game.allied(game.player, other) ? msg(() -> game.breakAlliance(game.player, other))
                : game.proposeAlliance(game.player, other)));
        pact.addActionListener(e -> act(() -> game.hasPact(game.player, other) ? msg(() -> game.cancelPact(game.player, other))
                : game.proposePact(game.player, other)));
        guarantee.addActionListener(e -> act(() -> game.guarantees(game.player, other) ? msg(() -> game.revokeGuarantee(game.player, other))
                : msgB(() -> game.guarantee(game.player, other))));
        trade.addActionListener(e -> act(() -> game.hasTrade(game.player, other) ? msg(() -> game.cancelTrade(game.player, other))
                : game.proposeTrade(game.player, other)));
        goalBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, ((Game.WarGoal) v).label(), i, sel, focus);
            }
        });
        goalBox.addActionListener(e -> {
            if (game != null) game.nextWarGoal = (Game.WarGoal) goalBox.getSelectedItem();
        });
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        JPanel buttons = new JPanel(new GridLayout(4, 1, 0, 4));
        buttons.add(alliance);
        buttons.add(pact);
        buttons.add(guarantee);
        buttons.add(trade);
        JPanel goal = new JPanel(new GridLayout(2, 1, 0, 2));
        goal.add(goalLabel);
        goal.add(goalBox);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(title, BorderLayout.NORTH);
        top.add(status, BorderLayout.CENTER);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(buttons, BorderLayout.CENTER);
        root.add(goal, BorderLayout.SOUTH);
    }

    private interface Action {
        String run();
    }

    private void act(Action a) {
        if (game == null || game.player == null || other == null) return;
        String m = a.run();
        if (m != null && !m.isEmpty()) game.log(m);
    }

    /** Runs a void action; used where the game itself reports the result. */
    private static String msg(Runnable r) {
        r.run();
        return null;
    }

    private static String msgB(java.util.function.BooleanSupplier r) {
        r.getAsBoolean();
        return null;
    }

    @Override
    public String titleKey() {
        return "tab.diplomacy";
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public void applyLanguage() {
        goalLabel.setText(I18n.tr("diplomacy.goal"));
        goalBox.repaint();
    }

    @Override
    public void refresh(Game g, Province p) {
        game = g;
        Country me = g.player;
        other = me != null && p != null && p.owner != null && p.owner != me ? p.owner : null;
        if (goalBox.getSelectedItem() != g.nextWarGoal) goalBox.setSelectedItem(g.nextWarGoal);
        if (other == null) {
            title.setText(I18n.tr("diplomacy.select"));
            status.setText("");
            for (JButton b : new JButton[] {alliance, pact, guarantee, trade}) {
                b.setText("-");
                b.setEnabled(false);
            }
            return;
        }
        title.setText(other.displayName());
        boolean war = g.atWar(me, other);
        StringBuilder sb = new StringBuilder();
        sb.append(war ? I18n.tr("diplomacy.war") : I18n.tr("diplomacy.peace")).append('\n');
        if (g.allied(me, other)) sb.append(I18n.tr("diplomacy.allied")).append('\n');
        if (g.hasPact(me, other)) sb.append(I18n.tr("diplomacy.pact", Game.formatDate(g.pactEnds(me, other)))).append('\n');
        if (g.guarantees(me, other)) sb.append(I18n.tr("diplomacy.guarantee.we")).append('\n');
        if (g.guarantees(other, me)) sb.append(I18n.tr("diplomacy.guarantee.they")).append('\n');
        if (g.hasTrade(me, other)) sb.append(I18n.tr("diplomacy.trade")).append('\n');
        status.setText(sb.toString());
        alliance.setText(I18n.tr(g.allied(me, other) ? "diplomacy.alliance.break" : "diplomacy.alliance.propose"));
        pact.setText(I18n.tr(g.hasPact(me, other) ? "diplomacy.pact.cancel" : "diplomacy.pact.propose"));
        guarantee.setText(I18n.tr(g.guarantees(me, other) ? "diplomacy.guarantee.revoke" : "diplomacy.guarantee.give"));
        trade.setText(I18n.tr(g.hasTrade(me, other) ? "diplomacy.trade.cancel" : "diplomacy.trade.propose"));
        alliance.setEnabled(!war);
        pact.setEnabled(!war);
        guarantee.setEnabled(g.guarantees(me, other) || !war);
        trade.setEnabled(!war);
    }
}
