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
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.Policy;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Taxes and conscription: gold against manpower. */
final class PolicyTab implements NationTab {
    private final JPanel root = new JPanel(new BorderLayout(0, 10));
    private final JLabel taxesLabel = new JLabel(), draftLabel = new JLabel(), summary = new JLabel(), cooldown = new JLabel();
    private final JComboBox<Policy.Taxes> taxesBox = new JComboBox<>(Policy.Taxes.values());
    private final JComboBox<Policy.Conscription> draftBox = new JComboBox<>(Policy.Conscription.values());
    private final JButton apply = new JButton();
    private Game game;

    PolicyTab() {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                String text = v instanceof Policy.Taxes t ? t.label() : ((Policy.Conscription) v).label();
                return super.getListCellRendererComponent(l, text, i, sel, focus);
            }
        };
        taxesBox.setRenderer(renderer);
        draftBox.setRenderer(renderer);
        apply.setFocusable(false);
        apply.addActionListener(e -> {
            Country me = game == null ? null : game.player;
            if (me == null) return;
            if (!game.setPolicies(me, (Policy.Taxes) taxesBox.getSelectedItem(), (Policy.Conscription) draftBox.getSelectedItem())) {
                game.log(I18n.tr("policy.wait", game.policyCooldown(me)));
            }
        });
        JPanel form = new JPanel(new GridLayout(5, 1, 0, 4));
        form.add(taxesLabel);
        form.add(taxesBox);
        form.add(draftLabel);
        form.add(draftBox);
        form.add(apply);
        JPanel info = new JPanel(new GridLayout(2, 1, 0, 4));
        info.add(summary);
        info.add(cooldown);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(form, BorderLayout.NORTH);
        root.add(info, BorderLayout.CENTER);
    }

    @Override
    public String titleKey() {
        return "tab.policy";
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public void applyLanguage() {
        taxesLabel.setText(I18n.tr("policy.taxes"));
        draftLabel.setText(I18n.tr("policy.conscription"));
        apply.setText(I18n.tr("policy.apply"));
        taxesBox.repaint();
        draftBox.repaint();
    }

    @Override
    public void refresh(Game g, Province p) {
        game = g;
        Country me = g.player;
        if (me == null) return;
        summary.setText(I18n.tr("policy.summary", (int) Math.round(me.taxes.gold * me.conscription.gold * 100),
                (int) Math.round(me.taxes.manpower * me.conscription.manpower * 100)));
        int wait = g.policyCooldown(me);
        cooldown.setText(wait > 0 ? I18n.tr("policy.cooldown", wait) : I18n.tr("policy.free"));
        apply.setEnabled(wait == 0);
        if (synced != me) {
            synced = me;
            syncedTaxes = me.taxes;
            syncedDraft = me.conscription;
            taxesBox.setSelectedItem(me.taxes);
            draftBox.setSelectedItem(me.conscription);
        } else {
            if (syncedTaxes != me.taxes) taxesBox.setSelectedItem(me.taxes);
            if (syncedDraft != me.conscription) draftBox.setSelectedItem(me.conscription);
            syncedTaxes = me.taxes;
            syncedDraft = me.conscription;
        }
    }

    private Country synced;
    private Policy.Taxes syncedTaxes;
    private Policy.Conscription syncedDraft;
}
