package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Table of all nations with their key figures; the player picks his nation here before the game starts. */
final class NationPickerDialog extends JDialog {
    interface Chooser {
        void choose(Country c);
    }

    private static final String[] COLUMNS = {"picker.nation", "picker.provinces", "picker.population", "picker.income", "picker.manpower", "picker.army"};

    private final List<Country> rows = new ArrayList<>();
    private final AbstractTableModel model;
    private final JTable table;
    private final JLabel hint = new JLabel();
    private final JButton choose = new JButton();
    private Object[][] data = new Object[0][];

    NationPickerDialog(JFrame owner, Chooser chooser) {
        super(owner, "", true);
        model = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return data.length;
            }

            @Override
            public int getColumnCount() {
                return COLUMNS.length;
            }

            @Override
            public Object getValueAt(int r, int c) {
                return data[r][c];
            }

            @Override
            public String getColumnName(int c) {
                return I18n.tr(COLUMNS[c]);
            }

            @Override
            public Class<?> getColumnClass(int c) {
                return c == 0 ? String.class : c == 3 ? Double.class : Integer.class;
            }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        Runnable pick = () -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            chooser.choose(rows.get(table.convertRowIndexToModel(row)));
            setVisible(false);
        };
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) pick.run();
            }
        });
        choose.setFocusable(false);
        choose.addActionListener(e -> pick.run());
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        south.add(hint, BorderLayout.CENTER);
        south.add(choose, BorderLayout.EAST);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(700, 520));
        pack();
        setLocationRelativeTo(owner);
        I18n.addListener(this::applyLanguage);
        applyLanguage();
    }

    private void applyLanguage() {
        setTitle(I18n.tr("picker.title"));
        hint.setText(I18n.tr("picker.hint"));
        choose.setText(I18n.tr("picker.choose"));
        model.fireTableStructureChanged();
    }

    void open(Game g) {
        rows.clear();
        List<Object[]> list = new ArrayList<>();
        for (Country c : g.world.countries) {
            if (!c.alive || c.provinces.isEmpty()) continue;
            long pop = 0;
            for (Province p : c.provinces) pop += p.population;
            rows.add(c);
            list.add(new Object[] {c.displayName(), c.provinces.size(), (int) (pop / 1000), g.monthlyIncome(c), g.monthlyManpower(c), (int) g.strength(c)});
        }
        data = list.toArray(new Object[0][]);
        model.fireTableDataChanged();
        List<RowSorter.SortKey> keys = new ArrayList<>();
        keys.add(new RowSorter.SortKey(1, SortOrder.DESCENDING));
        ((TableRowSorter<?>) table.getRowSorter()).setSortKeys(keys);
        setVisible(true);
    }
}
