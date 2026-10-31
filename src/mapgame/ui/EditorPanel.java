package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.sim.MapEditor;

/**
 * The map editor: change who owns which province, create and rename nations, rename provinces and set their
 * population, then save the result as a scenario for the {@code mods/scenarios} folder.
 */
final class EditorPanel extends JPanel {
    private final MapPanel map = new MapPanel();
    private final DefaultListModel<Country> nationModel = new DefaultListModel<>();
    private final JList<Country> nationList = new JList<>(nationModel);
    private final JComboBox<MapKind> kindBox = new JComboBox<>(MapKind.values());
    private final JTextField seedField = new JTextField("1", 8);
    private final JToggleButton paint = new JToggleButton();
    private final JButton newMapBtn = new JButton(), newNationBtn = new JButton(), renameBtn = new JButton(), applyBtn = new JButton(),
            saveBtn = new JButton(), backBtn = new JButton();
    private final JLabel kindLabel = new JLabel(), seedLabel = new JLabel(), nationsLabel = new JLabel(), provinceLabel = new JLabel(),
            nameLabel = new JLabel(), populationLabel = new JLabel(), idLabel = new JLabel(), yearLabel = new JLabel(), status = new JLabel(" ");
    private final JTextField provinceName = new JTextField(14), scenarioName = new JTextField("My scenario", 12), scenarioId = new JTextField("my_scenario", 12);
    private final JSpinner population = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000_000, 10_000));
    private final JSpinner year = new JSpinner(new SpinnerNumberModel(1900, 1900, 2500, 1));
    private MapEditor editor;
    private Province shown;
    private final Runnable onBack;

    EditorPanel(Runnable onBack) {
        super(new BorderLayout());
        this.onBack = onBack;
        nationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nationList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                Country c = (Country) v;
                JLabel label = (JLabel) super.getListCellRendererComponent(l, c.displayName() + " (" + c.provinces.size() + ")", i, sel, focus);
                label.setIcon(new ColorIcon(new Color(c.rgb)));
                return label;
            }
        });
        paint.setSelected(true);
        paint.setFocusable(false);
        for (JButton b : new JButton[] {newMapBtn, newNationBtn, renameBtn, applyBtn, saveBtn, backBtn}) b.setFocusable(false);

        newMapBtn.addActionListener(e -> createMap());
        newNationBtn.addActionListener(e -> newNation());
        renameBtn.addActionListener(e -> renameNation());
        applyBtn.addActionListener(e -> applyProvince());
        saveBtn.addActionListener(e -> saveScenario());
        backBtn.addActionListener(e -> onBack.run());
        map.setClickHandler(this::clicked);
        map.setShowArmies(false);
        map.setSelectionListener(this::selectionChanged);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(kindLabel);
        top.add(kindBox);
        top.add(seedLabel);
        top.add(seedField);
        top.add(newMapBtn);
        top.add(paint);
        top.add(backBtn);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout2(side));
        side.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        side.add(nationsLabel);
        JScrollPane listScroll = new JScrollPane(nationList);
        listScroll.setPreferredSize(new Dimension(260, 240));
        side.add(listScroll);
        JPanel nationButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        nationButtons.add(newNationBtn);
        nationButtons.add(renameBtn);
        side.add(nationButtons);
        side.add(provinceLabel);
        JPanel form = new JPanel(new GridLayout(3, 2, 4, 4));
        form.add(idLabel);
        form.add(new JLabel());
        form.add(nameLabel);
        form.add(provinceName);
        form.add(populationLabel);
        form.add(population);
        side.add(form);
        side.add(applyBtn);
        JPanel save = new JPanel(new GridLayout(3, 2, 4, 4));
        save.add(new JLabel("ID"));
        save.add(scenarioId);
        save.add(new JLabel("Name"));
        save.add(scenarioName);
        save.add(yearLabel);
        save.add(year);
        side.add(save);
        side.add(saveBtn);
        side.add(status);
        for (Component c : side.getComponents()) ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);

        add(top, BorderLayout.NORTH);
        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, map, new JScrollPane(side)) {
            {
                setResizeWeight(1.0);
                setDividerLocation(1000);
            }
        }, BorderLayout.CENTER);
        new Timer(100, e -> {
            if (editor != null && isShowing()) {
                map.frame();
                refreshList();
            }
        }).start();
        I18n.addListener(this::applyLanguage);
        applyLanguage();
    }

    private void applyLanguage() {
        kindLabel.setText(I18n.tr("editor.map"));
        seedLabel.setText(I18n.tr("editor.seed"));
        newMapBtn.setText(I18n.tr("editor.newmap"));
        paint.setText(I18n.tr("editor.paint"));
        backBtn.setText(I18n.tr("editor.back"));
        nationsLabel.setText(I18n.tr("editor.nations"));
        newNationBtn.setText(I18n.tr("editor.newnation"));
        renameBtn.setText(I18n.tr("editor.rename"));
        provinceLabel.setText(I18n.tr("editor.province"));
        nameLabel.setText(I18n.tr("editor.provname"));
        populationLabel.setText(I18n.tr("editor.population"));
        applyBtn.setText(I18n.tr("editor.apply"));
        yearLabel.setText(I18n.tr("editor.year"));
        saveBtn.setText(I18n.tr("editor.save"));
        paint.setToolTipText(I18n.tr("editor.paint.tip"));
        nationList.repaint();
        kindBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, ((MapKind) v).label(), i, sel, focus);
            }
        });
    }

    /** Called when the editor page is opened. */
    void open() {
        if (editor == null) createMap();
    }

    private void createMap() {
        MapKind kind = (MapKind) kindBox.getSelectedItem();
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            seed = 1;
            seedField.setText("1");
        }
        final long s = seed;
        status.setText(I18n.tr("editor.generating"));
        newMapBtn.setEnabled(false);
        new SwingWorker<MapEditor, Void>() {
            @Override
            protected MapEditor doInBackground() {
                return new MapEditor(kind, s);
            }

            @Override
            protected void done() {
                try {
                    editor = get();
                    map.setGame(editor.game);
                    map.frame();
                    nationList.clearSelection();
                    listedNations = -1;
                    listedProvinces = -1;
                    refreshList();
                    status.setText(" ");
                } catch (Exception ex) {
                    status.setText(String.valueOf(ex.getCause()));
                } finally {
                    newMapBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private int listedNations = -1, listedProvinces = -1;

    private void refreshList() {
        if (editor == null) return;
        int total = 0;
        for (Country c : editor.nations()) total += c.provinces.size() * 31 + c.id;
        int count = editor.nations().size();
        if (count == listedNations && total == listedProvinces) return;
        listedNations = count;
        listedProvinces = total;
        Country keep = nationList.getSelectedValue();
        nationModel.clear();
        for (Country c : editor.nations()) nationModel.addElement(c);
        if (keep != null && keep.alive) nationList.setSelectedValue(keep, true);
    }

    private void clicked(Province p) {
        if (editor == null || !p.isLand()) return;
        Country c = nationList.getSelectedValue();
        if (paint.isSelected() && c != null && editor.assign(p, c)) {
            map.frame();
            refreshList();
        }
    }

    private void selectionChanged() {
        Province p = map.selectedProvince();
        shown = p != null && p.isLand() ? p : null;
        if (shown == null) {
            idLabel.setText(" ");
            return;
        }
        idLabel.setText("#" + shown.id + (shown.owner == null ? "" : " - " + shown.owner.displayName()));
        provinceName.setText(shown.name);
        population.setValue(shown.population);
    }

    private void applyProvince() {
        if (editor == null || shown == null) return;
        editor.setName(shown, provinceName.getText());
        editor.setPopulation(shown, (Integer) population.getValue());
    }

    private void newNation() {
        if (editor == null) return;
        String name = JOptionPane.showInputDialog(this, I18n.tr("editor.nation.name"), I18n.tr("editor.newnation"), JOptionPane.QUESTION_MESSAGE);
        if (name == null) return;
        Color color = JColorChooser.showDialog(this, I18n.tr("editor.nation.color"), new Color(0x5BA3D9));
        if (color == null) return;
        Country c = editor.newCountry(name.strip(), color.getRGB());
        if (c == null) {
            status.setText(I18n.tr("editor.nation.invalid"));
            return;
        }
        refreshList();
        nationList.setSelectedValue(c, true);
        status.setText(I18n.tr("editor.nation.created"));
    }

    private void renameNation() {
        Country c = nationList.getSelectedValue();
        if (editor == null || c == null) return;
        String name = JOptionPane.showInputDialog(this, I18n.tr("editor.nation.name"), c.name);
        if (name == null) return;
        if (!editor.rename(c, name.strip())) status.setText(I18n.tr("editor.nation.invalid"));
        nationList.repaint();
    }

    private void saveScenario() {
        if (editor == null) return;
        try {
            Path file = editor.save(scenarioId.getText().trim(), scenarioName.getText().strip(), (Integer) year.getValue());
            status.setText(I18n.tr("editor.saved", file));
        } catch (IOException ex) {
            status.setText(ex.getMessage());
        }
    }

    /** A small coloured square in front of the nation's name. */
    private record ColorIcon(Color color) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, 14, 14);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, 14, 14);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    /** Vertical box layout with the components stretched to the full width. */
    private static final class BoxLayout2 extends javax.swing.BoxLayout {
        BoxLayout2(JPanel target) {
            super(target, javax.swing.BoxLayout.Y_AXIS);
        }
    }
}
