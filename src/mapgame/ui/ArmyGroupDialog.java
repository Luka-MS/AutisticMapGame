package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.Country;
import mapgame.sim.Game;

/** Non-modal window to create army groups, give them a front and a stance. */
public final class ArmyGroupDialog extends JDialog {
    private final MapPanel map;
    private Game game;

    private final DefaultListModel<ArmyGroup> listModel = new DefaultListModel<>();
    private final JList<ArmyGroup> list = new JList<>(listModel);
    private final JComboBox<Country> targetBox = new JComboBox<>();
    private final JComboBox<ArmyGroup.Stance> stanceBox = new JComboBox<>(ArmyGroup.Stance.values());
    private final JTextArea statusArea = new JTextArea(6, 24);
    private final JButton newBtn = new JButton("New group"), deleteBtn = new JButton("Delete group"),
            addBtn = new JButton("Add selected army"), removeBtn = new JButton("Remove selected army"),
            addAllBtn = new JButton("Add all free armies");
    private boolean updating;

    public ArmyGroupDialog(JFrame owner, MapPanel map) {
        super(owner, "Army groups", false);
        this.map = map;
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        ListRenderer renderer = new ListRenderer();
        list.setCellRenderer(renderer);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refresh();
        });
        targetBox.setRenderer(renderer);
        stanceBox.setRenderer(renderer);
        targetBox.addActionListener(e -> {
            ArmyGroup g = list.getSelectedValue();
            if (updating || g == null) return;
            g.target = (Country) targetBox.getSelectedItem();
            game.planGroup(g);
            refresh();
        });
        stanceBox.addActionListener(e -> {
            ArmyGroup g = list.getSelectedValue();
            if (updating || g == null) return;
            g.stance = (ArmyGroup.Stance) stanceBox.getSelectedItem();
            game.planGroup(g);
            refresh();
        });
        newBtn.addActionListener(e -> {
            ArmyGroup g = game.createGroup(game.player);
            Army a = map.selectedArmy();
            if (a != null && a.owner == game.player && a.group == null) game.assignToGroup(g, a);
            sync();
            list.setSelectedValue(g, true);
            refresh();
        });
        deleteBtn.addActionListener(e -> {
            ArmyGroup g = list.getSelectedValue();
            if (g != null) game.deleteGroup(g);
            sync();
            refresh();
        });
        addBtn.addActionListener(e -> {
            ArmyGroup g = list.getSelectedValue();
            boolean added = false;
            for (Army a : map.selectedArmies()) {
                if (g != null && a.owner == game.player && game.assignToGroup(g, a)) added = true;
            }
            if (added) game.planGroup(g);
            refresh();
        });
        removeBtn.addActionListener(e -> {
            game.releaseFromGroup(map.selectedArmy());
            refresh();
        });
        addAllBtn.addActionListener(e -> {
            ArmyGroup g = list.getSelectedValue();
            if (g == null) return;
            for (Army a : game.armiesOf(game.player)) if (a.group == null) game.assignToGroup(g, a);
            game.planGroup(g);
            refresh();
        });

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(340, 130));

        JPanel groupButtons = new JPanel(new GridLayout(1, 2, 6, 0));
        groupButtons.add(newBtn);
        groupButtons.add(deleteBtn);
        JPanel armyButtons = new JPanel(new GridLayout(1, 2, 6, 0));
        armyButtons.add(addBtn);
        armyButtons.add(removeBtn);

        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(left(new JLabel("Army groups")));
        form.add(Box.createVerticalStrut(4));
        form.add(left(listScroll));
        form.add(Box.createVerticalStrut(6));
        form.add(left(groupButtons));
        form.add(Box.createVerticalStrut(10));
        form.add(left(new JLabel("Front against:")));
        form.add(left(targetBox));
        form.add(Box.createVerticalStrut(6));
        form.add(left(new JLabel("Orders:")));
        form.add(left(stanceBox));
        form.add(Box.createVerticalStrut(10));
        form.add(left(armyButtons));
        form.add(Box.createVerticalStrut(4));
        form.add(left(addAllBtn));
        form.add(Box.createVerticalStrut(10));
        form.add(left(new JScrollPane(statusArea)));

        JLabel help = new JLabel("<html>The group spreads its men evenly over the front, splits armies "
                + "to cover it and attacks or defends automatically. Giving a manual move order to an army "
                + "removes it from its group.</html>");
        help.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(help, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(380, 620));
        pack();
        setLocationRelativeTo(owner);
    }

    public void bind(Game g) {
        game = g;
        listModel.clear();
        map.setShownGroup(null);
        refresh();
    }

    /** Brings the group list in line with the game's groups. */
    private void sync() {
        ArmyGroup sel = list.getSelectedValue();
        if (game == null) {
            listModel.clear();
            return;
        }
        boolean same = listModel.size() == game.groups.size();
        for (int i = 0; same && i < listModel.size(); i++) same = listModel.get(i) == game.groups.get(i);
        if (same) return;
        listModel.clear();
        for (ArmyGroup g : game.groups) listModel.addElement(g);
        if (sel != null && game.groups.contains(sel)) list.setSelectedValue(sel, true);
    }

    public void refresh() {
        if (game == null || game.player == null) {
            for (JButton b : new JButton[] {newBtn, deleteBtn, addBtn, removeBtn, addAllBtn}) b.setEnabled(false);
            targetBox.setEnabled(false);
            stanceBox.setEnabled(false);
            return;
        }
        updating = true;
        try {
            sync();
            ArmyGroup g = list.getSelectedValue();
            List<Country> enemies = game.enemiesOf(game.player);
            List<Country> items = new ArrayList<>();
            items.add(null);
            items.addAll(enemies);
            if (!sameItems(items)) targetBox.setModel(new DefaultComboBoxModel<>(items.toArray(new Country[0])));
            Army a = map.selectedArmy();
            boolean mine = a != null && !a.dead && a.owner == game.player;

            newBtn.setEnabled(true);
            deleteBtn.setEnabled(g != null);
            targetBox.setEnabled(g != null);
            stanceBox.setEnabled(g != null);
            addBtn.setEnabled(g != null && mine && a.group != g);
            removeBtn.setEnabled(mine && a.group != null);
            addAllBtn.setEnabled(g != null);

            if (g == null) {
                targetBox.setSelectedItem(null);
                setStatus("Create a group with 'New group', then pick a front.");
                map.setShownGroup(null);
            } else {
                targetBox.setSelectedItem(items.contains(g.target) ? g.target : null);
                stanceBox.setSelectedItem(g.stance);
                setStatus(String.format("%s%n%nArmies: %d   Men: %,d%n%s", g.name, g.armies.size(), g.strength(), g.status));
                map.setShownGroup(isVisible() ? g : null);
            }
            list.repaint();
        } finally {
            updating = false;
        }
    }

    private boolean sameItems(List<Country> items) {
        if (targetBox.getItemCount() != items.size()) return false;
        for (int i = 0; i < items.size(); i++) if (targetBox.getItemAt(i) != items.get(i)) return false;
        return true;
    }

    private void setStatus(String s) {
        if (!statusArea.getText().equals(s)) statusArea.setText(s);
    }

    private static JPanel left(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    private static final class ListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
            String text;
            if (v == null) text = "- none -";
            else if (v instanceof ArmyGroup g) text = g.name + "  (" + g.armies.size() + " armies, " + String.format("%,d", g.strength()) + " men)";
            else if (v instanceof Country c) text = c.name;
            else if (v instanceof ArmyGroup.Stance s) text = s.label;
            else text = v.toString();
            return super.getListCellRendererComponent(l, text, i, sel, focus);
        }
    }
}
