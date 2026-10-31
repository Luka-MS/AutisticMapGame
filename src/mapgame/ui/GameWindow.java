package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import mapgame.i18n.I18n;
import mapgame.model.Army;
import mapgame.model.ArmyType;
import mapgame.model.Building;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.sim.Difficulty;
import mapgame.sim.Game;
import mapgame.sim.SaveFile;

public final class GameWindow extends JFrame {
    /** Real milliseconds per in-game day for speeds 1..5 (index 0 = paused). */
    private static final int[] DAY_MS = {0, 1000, 500, 250, 120, 40};

    private final MapPanel map = new MapPanel();
    private final JLabel dateLabel = new JLabel();
    private final JTextArea status = textArea(5), info = textArea(8), logArea = new JTextArea();
    private final JButton startBtn = button(), pickBtn = button(), recruitBtn = button(), splitBtn = button(), mergeBtn = button(),
            groupsBtn = button(), warBtn = button(), totalWarBtn = button(), peaceBtn = button(), allianceBtn = button(), nationBtn = button(),
            menuBtn = button(), saveBtn = button(), loadBtn = button(), modeBtn = button(), fogBtn = button();
    private final List<JToggleButton> speedBtns = new ArrayList<>();
    private JSplitPane sideSplit;
    private final ArmyGroupDialog groupDialog;
    private final NationDialog nationDialog;
    private final NationPickerDialog pickerDialog;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final MainMenuPanel menu;
    private final JTabbedPane logTabs = new JTabbedPane();
    private final JComboBox<ArmyType> typeBox = new JComboBox<>(ArmyType.values());
    private final DefaultListModel<Game.Event> eventModel = new DefaultListModel<>();
    private final JList<Game.Event> eventList = new JList<>(eventModel);
    private int unreadEvents;

    private static final Path SAVE_DIR = Paths.get("saves");
    private static final Path QUICKSAVE = SAVE_DIR.resolve("quicksave" + SaveFile.EXTENSION);
    private static final Path AUTOSAVE = SAVE_DIR.resolve("autosave" + SaveFile.EXTENSION);

    private Game game;
    private boolean started;
    private boolean inMenu = true;
    private boolean modalOpen;
    private boolean dividerPlaced;
    private int speed;
    private int lastAutosaveYear;
    private double acc;
    private long lastNanos = System.nanoTime();
    private long lastHud;

    public GameWindow() {
        super("Autistic Map Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel gamePanel = new JPanel(new BorderLayout());
        gamePanel.add(map, BorderLayout.CENTER);
        gamePanel.add(buildSidePanel(), BorderLayout.EAST);
        menu = new MainMenuPanel(new MainMenuPanel.Listener() {
            @Override
            public void play(MapKind kind, long seed) {
                startNewGame(kind, seed);
            }

            @Override
            public void load() {
                loadWithChooser();
            }

            @Override
            public void resume() {
                showGame();
            }

            @Override
            public void quit() {
                dispose();
                System.exit(0);
            }
        });
        root.add(menu, "menu");
        root.add(gamePanel, "game");
        setContentPane(root);
        installKeys();
        I18n.addListener(this::applyLanguage);
        map.setSelectionListener(this::refresh);
        setSize(1380, 820);
        setLocationRelativeTo(null);
        groupDialog = new ArmyGroupDialog(this, map);
        nationDialog = new NationDialog(this, map);
        pickerDialog = new NationPickerDialog(this, c -> map.showProvince(c.capital));
        pickBtn.addActionListener(e -> {
            if (game != null && !started) pickerDialog.open(game);
        });
        applyLanguage();

        startBtn.addActionListener(e -> startGame());
        menuBtn.addActionListener(e -> showMenu());
        saveBtn.addActionListener(e -> saveWithChooser());
        loadBtn.addActionListener(e -> loadWithChooser());
        modeBtn.addActionListener(e -> toggleMode());
        fogBtn.addActionListener(e -> toggleFog());
        groupsBtn.addActionListener(e -> showGroups());
        nationBtn.addActionListener(e -> showNation(null));
        recruitBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p != null && game.recruit(game.player, p, (ArmyType) typeBox.getSelectedItem()) == null) game.log(I18n.tr("log.cannot.recruit"));
            refresh();
        });
        typeBox.setFocusable(false);
        typeBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                ArmyType t = (ArmyType) value;
                return super.getListCellRendererComponent(list, I18n.tr("unit.item", t.label(), Game.recruitGold(t)), index, selected, focus);
            }
        });
        typeBox.addActionListener(e -> refresh());
        splitBtn.addActionListener(e -> splitSelected());
        mergeBtn.addActionListener(e -> mergeSelected());
        warBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p != null && p.owner != null) game.declareWar(game.player, p.owner);
            refresh();
        });
        totalWarBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p == null || p.owner == null) return;
            int answer = JOptionPane.showConfirmDialog(this, I18n.tr("dlg.total.confirm", p.owner.displayName()),
                    I18n.tr("dlg.total.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) game.declareWar(game.player, p.owner, true);
            refresh();
        });
        peaceBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p != null && p.owner != null) game.log(game.proposePeace(game.player, p.owner));
            refresh();
        });
        allianceBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p == null || p.owner == null) return;
            if (game.allied(game.player, p.owner)) game.breakAlliance(game.player, p.owner);
            else game.log(game.proposeAlliance(game.player, p.owner));
            refresh();
        });

        new Timer(16, e -> frame()).start();
    }

    public void begin() {
        setVisible(true);
        showMenu();
    }

    private void showMenu() {
        inMenu = true;
        groupDialog.setVisible(false);
        nationDialog.setVisible(false);
        menu.setBusy(null);
        menu.setCanResume(game != null);
        cards.show(root, "menu");
    }

    private void showGame() {
        inMenu = false;
        lastNanos = System.nanoTime();
        cards.show(root, "game");
        root.validate();
        if (!dividerPlaced) {
            dividerPlaced = true;
            SwingUtilities.invokeLater(() -> sideSplit.setDividerLocation(Math.max(200, sideSplit.getHeight() - 280)));
        }
    }

    /** Generates the world in the background, then switches from the menu to the game. */
    public void startNewGame(MapKind kind, long seed) {
        menu.setBusy(I18n.tr("menu.generating", kind.label()));
        Difficulty difficulty = menu.difficulty();
        int aggression = menu.aggression();
        new SwingWorker<Game, Void>() {
            @Override
            protected Game doInBackground() {
                Game g = Game.create(kind, seed);
                g.difficulty = difficulty;
                g.aggression = aggression;
                return g;
            }

            @Override
            protected void done() {
                try {
                    installGame(get(), false);
                } catch (Exception ex) {
                    menu.setBusy(null);
                    JOptionPane.showMessageDialog(GameWindow.this, I18n.tr("dlg.create.failed", ex.getCause()),
                            I18n.tr("dlg.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void installGame(Game g, boolean restored) {
        game = g;
        game.logSink = s -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        };
        eventModel.clear();
        unreadEvents = 0;
        game.decisions = this::askDecision;
        game.eventSink = ev -> {
            eventModel.add(0, ev);
            if (eventModel.size() > Game.MAX_EVENTS) eventModel.removeElementAt(eventModel.size() - 1);
            if (logTabs.getSelectedIndex() != 1) unreadEvents++;
            updateTabTitles();
        };
        updateTabTitles();
        started = restored && g.player != null;
        acc = 0;
        lastAutosaveYear = g.day / 360;
        logArea.setText("");
        setSpeed(0);
        groupDialog.bind(game);
        nationDialog.bind(game);
        showGame();
        map.setGame(game);
        if (started) {
            map.centerOn(game.player.capital);
            game.log(I18n.tr("log.loaded", game.player.displayName()));
        } else {
            logArea.append(I18n.tr("log.pick") + "\n");
            logArea.append(I18n.tr("log.controls") + "\n");
        }
        refresh();
    }

    // ---------------------------------------------------------------- save / load

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(SAVE_DIR.toAbsolutePath().toFile());
        fc.setFileFilter(new FileNameExtensionFilter(I18n.tr("dlg.saves", SaveFile.EXTENSION), "sav"));
        return fc;
    }

    private void saveWithChooser() {
        if (game == null) return;
        try {
            Files.createDirectories(SAVE_DIR);
        } catch (IOException ignored) {
            // the chooser will simply open somewhere else
        }
        JFileChooser fc = chooser();
        String nation = started ? game.player.displayName() : I18n.tr("save.world");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), nation + " " + (1900 + game.day / 360) + SaveFile.EXTENSION));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = fc.getSelectedFile().toPath();
        if (!file.getFileName().toString().endsWith(SaveFile.EXTENSION)) file = file.resolveSibling(file.getFileName() + SaveFile.EXTENSION);
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, I18n.tr("dlg.save.overwrite", file.getFileName()), I18n.tr("dlg.save.title"),
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        saveTo(file);
    }

    private void saveTo(Path file) {
        if (game == null) return;
        try {
            SaveFile.save(game, file);
            game.log(I18n.tr("log.saved", file.getFileName()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, I18n.tr("dlg.save.failed", ex.getMessage()), I18n.tr("dlg.save.failed.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void quickSave() {
        if (!inMenu) saveTo(QUICKSAVE);
    }

    private void loadWithChooser() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadFrom(fc.getSelectedFile().toPath());
    }

    private void quickLoad() {
        if (Files.exists(QUICKSAVE)) loadFrom(QUICKSAVE);
        else if (game != null) game.log(I18n.tr("log.no.quicksave"));
    }

    /** Loads in the background (the map is recreated), then continues the game. */
    private void loadFrom(Path file) {
        menu.setBusy(I18n.tr("menu.loading", file.getFileName()));
        new SwingWorker<Game, Void>() {
            @Override
            protected Game doInBackground() throws IOException {
                return SaveFile.load(file);
            }

            @Override
            protected void done() {
                try {
                    installGame(get(), true);
                } catch (Exception ex) {
                    menu.setBusy(null);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(GameWindow.this, I18n.tr("dlg.load.failed", file.getFileName(), cause.getMessage()),
                            I18n.tr("dlg.load.failed.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void autosave() {
        try {
            SaveFile.save(game, AUTOSAVE);
            game.log(I18n.tr("log.autosaved"));
        } catch (IOException ex) {
            game.log(I18n.tr("log.autosave.failed", ex.getMessage()));
        }
    }

    private void showNation(String page) {
        if (!started) return;
        if (page == null) {
            nationDialog.setVisible(true);
            nationDialog.toFront();
            nationDialog.refresh();
        } else {
            nationDialog.show(page);
        }
    }

    private void showGroups() {
        if (!started) return;
        groupDialog.setVisible(true);
        groupDialog.toFront();
        groupDialog.refresh();
    }

    private boolean playerArmy(Army a) {
        return started && a != null && !a.dead && a.owner == game.player;
    }

    private void splitSelected() {
        Army a = map.selectedArmy();
        if (!playerArmy(a)) return;
        Army n = game.split(a);
        if (n == null) game.log(I18n.tr("log.cannot.split"));
        else map.selectArmy(n);
        refresh();
    }

    private void mergeSelected() {
        Army a = map.selectedArmy();
        if (!playerArmy(a)) return;
        if (game.mergeStack(a) == 0) game.log(I18n.tr("log.no.merge"));
        refresh();
    }

    // ---------------------------------------------------------------- setup

    private JComponent buildSidePanel() {
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 18f));
        head.add(left(dateLabel));
        head.add(Box.createVerticalStrut(6));

        JPanel speeds = new JPanel();
        speeds.setLayout(new GridLayout(1, 6, 4, 0));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i <= 5; i++) {
            final int s = i;
            JToggleButton b = new JToggleButton(i == 0 ? "||" : Integer.toString(i));
            b.setFocusable(false);
            b.addActionListener(e -> setSpeed(s));
            group.add(b);
            speedBtns.add(b);
            speeds.add(b);
        }
        speedBtns.get(0).setSelected(true);
        head.add(left(speeds));
        head.add(Box.createVerticalStrut(6));
        head.add(left(modeBtn));
        head.add(Box.createVerticalStrut(4));
        head.add(left(fogBtn));
        head.add(Box.createVerticalStrut(6));
        head.add(scroll(status, 100));

        JScrollPane infoScroll = new JScrollPane(info);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        for (JComponent b : new JComponent[] {startBtn, pickBtn, typeBox, recruitBtn, splitBtn, mergeBtn, groupsBtn, nationBtn, warBtn, totalWarBtn, peaceBtn, allianceBtn}) buttons.add(left(b));

        JPanel top = new WidthTrackingPanel(new BorderLayout(0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(head, BorderLayout.NORTH);
        top.add(infoScroll, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        // Scrollable so the divider can be dragged far up (big log) without clipping the controls.
        JScrollPane topScroll = new JScrollPane(top, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        topScroll.setBorder(null);
        topScroll.getVerticalScrollBar().setUnitIncrement(16);
        topScroll.setMinimumSize(new Dimension(300, 120));

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        eventList.setCellRenderer(new EventRenderer());
        eventList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = eventList.locationToIndex(e.getPoint());
                if (i < 0 || !eventList.getCellBounds(i, i).contains(e.getPoint())) return;
                Game.Event ev = eventModel.get(i);
                if (ev.where() != null) map.showProvince(ev.where());
            }
        });
        logTabs.addTab("", new JScrollPane(logArea));
        logTabs.addTab("", new JScrollPane(eventList));
        logTabs.addChangeListener(e -> {
            if (logTabs.getSelectedIndex() == 1) unreadEvents = 0;
            updateTabTitles();
        });
        bottom.add(logTabs, BorderLayout.CENTER);
        JPanel bottomButtons = new JPanel(new GridLayout(1, 3, 4, 0));
        for (JButton b : new JButton[] {saveBtn, loadBtn, menuBtn}) bottomButtons.add(b);
        bottom.add(bottomButtons, BorderLayout.SOUTH);
        bottom.setMinimumSize(new Dimension(300, 90));

        // Vertical splitter: dragging the divider up or down resizes the log.
        sideSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topScroll, bottom);
        sideSplit.setResizeWeight(1.0);
        sideSplit.setContinuousLayout(true);
        sideSplit.setDividerSize(8);
        sideSplit.setBorder(null);
        sideSplit.setPreferredSize(new Dimension(390, 0));
        return sideSplit;
    }

    private void installKeys() {
        bind("SPACE", () -> setSpeed(speed == 0 ? 1 : 0));
        for (int i = 1; i <= 5; i++) {
            final int s = i;
            bind(Integer.toString(i), () -> setSpeed(s));
        }
        bind("M", this::toggleMode);
        bind("F", this::toggleFog);
        bind("X", this::splitSelected);
        bind("C", this::mergeSelected);
        bind("G", this::showGroups);
        bind("N", () -> showNation(null));
        bind("H", this::goHome);
        bind("HOME", this::goHome);
        bind("F5", this::quickSave);
        bind("F9", this::quickLoad);
        bind("ESCAPE", map::clearSelection);
        for (String k : new String[] {"LEFT", "A"}) bind(k, () -> map.pan(60, 0));
        for (String k : new String[] {"RIGHT", "D"}) bind(k, () -> map.pan(-60, 0));
        for (String k : new String[] {"UP", "W"}) bind(k, () -> map.pan(0, 60));
        for (String k : new String[] {"DOWN", "S"}) bind(k, () -> map.pan(0, -60));
    }

    private void bind(String key, Runnable action) {
        String name = "key-" + key;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- actions

    private void startGame() {
        Province p = map.selectedProvince();
        if (p == null || p.owner == null) return;
        game.player = p.owner;
        started = true;
        game.log(I18n.tr("log.start", game.player.displayName()));
        map.centerOn(game.player.capital);
        setSpeed(1);
        refresh();
    }

    private void goHome() {
        if (started && game.player.capital != null) map.showProvince(game.player.capital);
    }

    private void setSpeed(int s) {
        speed = started ? s : 0;
        speedBtns.get(speed).setSelected(true);
    }

    /** Sets all fixed texts in the current language; the HUD texts follow in {@link #refresh()}. */
    private void applyLanguage() {
        UIManager.put("OptionPane.yesButtonText", I18n.tr("dlg.yes"));
        UIManager.put("OptionPane.noButtonText", I18n.tr("dlg.no"));
        UIManager.put("OptionPane.okButtonText", I18n.tr("dlg.ok"));
        UIManager.put("OptionPane.cancelButtonText", I18n.tr("dlg.cancel"));
        startBtn.setText(I18n.tr("btn.start"));
        pickBtn.setText(I18n.tr("btn.pick"));
        recruitBtn.setText(I18n.tr("btn.recruit", Game.RECRUIT_GOLD, Game.RECRUIT_MEN));
        splitBtn.setText(I18n.tr("btn.split"));
        mergeBtn.setText(I18n.tr("btn.merge"));
        groupsBtn.setText(I18n.tr("btn.groups"));
        nationBtn.setText(I18n.tr("btn.nation"));
        menuBtn.setText(I18n.tr("btn.menu"));
        saveBtn.setText(I18n.tr("btn.save"));
        loadBtn.setText(I18n.tr("btn.load"));
        updateModeText();
        updateTabTitles();
        typeBox.repaint();
        eventList.repaint();
        map.repaint();
        refresh();
    }

    private void updateTabTitles() {
        logTabs.setTitleAt(0, I18n.tr("tab.log"));
        logTabs.setTitleAt(1, unreadEvents > 0 ? I18n.tr("tab.events.new", unreadEvents) : I18n.tr("tab.events"));
    }

    /** One line of the notification list: date and text, wrapped. */
    private static final class EventRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Game.Event ev = (Game.Event) value;
            String text = Game.formatDate(ev.day()) + "  " + ev.text();
            JLabel l = (JLabel) super.getListCellRendererComponent(list, "", index, selected, focus);
            int width = Math.max(120, list.getWidth() - 24);
            l.setText("<html><body style='width:" + width + "px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
            l.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            l.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            l.setToolTipText(null);
            return l;
        }
    }

    private void updateModeText() {
        modeBtn.setText(I18n.tr("btn.mode", map.mode().label()));
    }

    private void toggleFog() {
        if (game == null || !started) return;
        game.fogOfWar = !game.fogOfWar;
        refresh();
    }

    private void toggleMode() {
        map.toggleMode();
        updateModeText();
    }

    /** Shows a decision event; the game stands still while the question is open. */
    private int askDecision(String title, String text, List<String> options) {
        int oldSpeed = speed;
        setSpeed(0);
        modalOpen = true;
        try {
            int choice = JOptionPane.showOptionDialog(this, "<html><body style='width:320px'>" + text + "</body></html>", title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options.toArray(), options.get(0));
            return Math.max(0, choice);
        } finally {
            modalOpen = false;
            lastNanos = System.nanoTime();
            setSpeed(oldSpeed);
        }
    }

    private void frame() {
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1e6;
        lastNanos = now;
        if (inMenu || modalOpen) return;
        if (started && speed > 0) {
            acc += dt;
            int steps = 0;
            while (acc >= DAY_MS[speed] && steps < 10) {
                acc -= DAY_MS[speed];
                game.tickDay();
                steps++;
            }
            if (steps == 10) acc = 0;
            if (game.day / 360 > lastAutosaveYear) {
                lastAutosaveYear = game.day / 360;
                autosave();
            }
            checkEnd();
        }
        map.frame();
        if (now - lastHud > 100_000_000L) {
            lastHud = now;
            refresh();
        }
    }

    private void checkEnd() {
        if (!started) return;
        if (!game.player.alive) {
            started = false;
            setSpeed(0);
            JOptionPane.showMessageDialog(this, I18n.tr("dlg.defeat", game.player.displayName()), I18n.tr("dlg.defeat.title"),
                    JOptionPane.WARNING_MESSAGE);
        } else if (game.aliveCountries().size() == 1) {
            started = false;
            setSpeed(0);
            JOptionPane.showMessageDialog(this, I18n.tr("dlg.victory"), I18n.tr("dlg.victory.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- HUD

    private void refresh() {
        if (game == null) return;
        Country me = game.player;
        Province sel = map.selectedProvince();
        dateLabel.setText(game.date());
        fogBtn.setText(I18n.tr("btn.fog", I18n.tr(game.fogOfWar ? "fog.on" : "fog.off")));
        fogBtn.setEnabled(started);

        if (started && me != null) {
            double net = game.monthlyIncome(me) - game.monthlyUpkeep(me);
            List<Country> foes = game.enemiesOf(me);
            StringBuilder wars = new StringBuilder();
            for (Country f : foes) wars.append(wars.length() > 0 ? ", " : "").append(f.displayName());
            set(status, I18n.tr("hud.status", me.displayName(), me.gold, net, me.manpower, game.monthlyManpower(me), me.provinces.size(),
                    game.strength(me), foes.isEmpty() ? I18n.tr("hud.nobody") : wars));
        } else {
            set(status, I18n.tr("hud.choose"));
        }
        set(info, describe(sel));

        startBtn.setVisible(!started);
        pickBtn.setVisible(!started);
        startBtn.setEnabled(sel != null && sel.owner != null);
        boolean playing = started && me != null;
        boolean own = playing && sel != null && sel.owner == me && sel.controller == me;
        boolean foreign = playing && sel != null && sel.owner != null && sel.owner != me;
        ArmyType recruitType = (ArmyType) typeBox.getSelectedItem();
        int recruitGold = Game.recruitGold(recruitType);
        recruitBtn.setText(I18n.tr("btn.recruit", recruitGold, Game.RECRUIT_MEN));
        recruitBtn.setEnabled(own && me.gold >= recruitGold && me.manpower >= Game.RECRUIT_MEN);
        typeBox.setEnabled(playing);
        Army sa = map.selectedArmy();
        boolean mine = playerArmy(sa);
        splitBtn.setEnabled(mine && game.canSplit(sa));
        mergeBtn.setEnabled(mine && game.mergeCandidates(sa) > 0);
        boolean atWarWithSel = foreign && game.atWar(me, sel.owner);
        boolean alliedWithSel = foreign && game.allied(me, sel.owner);
        warBtn.setEnabled(foreign && !atWarWithSel && !alliedWithSel && !game.hasPact(me, sel.owner));
        boolean totalWithSel = atWarWithSel && game.isTotalWar(me, sel.owner);
        totalWarBtn.setEnabled(foreign && !totalWithSel && !alliedWithSel && !game.hasPact(me, sel.owner));
        allianceBtn.setEnabled(foreign && !atWarWithSel);
        allianceBtn.setText(!foreign ? I18n.tr("btn.alliance")
                : I18n.tr(alliedWithSel ? "btn.alliance.break" : "btn.alliance.propose", sel.owner.displayName()));
        totalWarBtn.setText(!foreign ? I18n.tr("btn.total")
                : I18n.tr(atWarWithSel ? "btn.total.escalate" : "btn.total.on", sel.owner.displayName()));
        peaceBtn.setEnabled(atWarWithSel && !totalWithSel);
        peaceBtn.setText(I18n.tr(totalWithSel ? "btn.peace.none" : "btn.peace"));
        groupsBtn.setEnabled(playing);
        nationBtn.setEnabled(playing);
        nationDialog.refresh();
        warBtn.setText(foreign ? I18n.tr("btn.war.on", sel.owner.displayName()) : I18n.tr("btn.war"));
        if (groupDialog.isVisible()) groupDialog.refresh();
    }

    private String describe(Province p) {
        if (p == null) return I18n.tr("info.none");
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.tr("info.name", p.name, p.terrain.label())).append('\n');
        if (!p.isLand()) return sb.append(I18n.tr("info.sea")).toString();
        sb.append(I18n.tr("info.population", p.population, p.income(game.day))).append('\n');
        if (p.city != null) sb.append(I18n.tr("info.city", p.city, p.cityPopulation)).append('\n');
        if (p.isCapital()) sb.append(I18n.tr("info.capital", p.owner.displayName())).append('\n');
        sb.append(I18n.tr("info.owner", p.owner == null ? I18n.tr("info.unclaimed") : p.owner.displayName())).append('\n');
        if (p.owner != null && p.controller != p.owner) sb.append(I18n.tr("info.occupied", p.controller.displayName())).append('\n');
        if (!p.ferryTo.isEmpty()) sb.append(I18n.tr("info.ferry", Province.FERRY_DAYS)).append('\n');
        sb.append(I18n.tr("info.travel", p.terrain.moveDays)).append('\n');
        StringBuilder built = new StringBuilder();
        for (Building b : Building.values()) {
            if (p.level(b) > 0) built.append(built.length() > 0 ? ", " : "").append(b.label()).append(' ').append(p.level(b));
        }
        if (built.length() > 0) sb.append(I18n.tr("info.buildings", built)).append('\n');
        if (p.unrest > 0) sb.append(I18n.tr("info.unrest", (int) p.unrest)).append('\n');
        if (p.owner != null) {
            Country c = p.owner;
            sb.append('\n').append(I18n.tr("info.country", c.displayName(), c.provinces.size(), game.strength(c))).append('\n');
            if (started && c != game.player) {
                if (game.atWar(game.player, c)) {
                    sb.append(I18n.tr(game.isTotalWar(game.player, c) ? "info.war.total" : "info.war", game.warScore(game.player, c)));
                } else if (game.allied(game.player, c)) {
                    sb.append(I18n.tr("info.allied"));
                } else {
                    sb.append(I18n.tr("info.peace"));
                }
            }
            List<Country> allies = game.alliesOf(c);
            if (!allies.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (Country al : allies) names.append(names.length() > 0 ? ", " : "").append(al.displayName());
                sb.append('\n').append(I18n.tr("info.allies", names));
            }
        }
        Army a = map.selectedArmy();
        if (a != null && !a.dead) {
            String state = game.isEngaged(a) ? I18n.tr("army.battle")
                    : a.path.isEmpty() ? I18n.tr("army.idle") : I18n.tr("army.marching", lastOf(a).name);
            List<Army> all = map.selectedArmies();
            if (all.size() > 1) {
                int men = 0;
                for (Army x : all) men += x.strength;
                sb.append("\n\n").append(I18n.tr("info.armies", all.size(), men));
            } else {
                sb.append("\n\n").append(I18n.tr("info.army", a.owner.displayName(), a.strength, state));
                double loss = game.attritionRate(a) * 6 * 100;
                sb.append('\n').append(loss <= 0 ? I18n.tr("info.supply.ok") : I18n.tr("info.supply.bad", loss));
                sb.append('\n').append(I18n.tr("info.army.type", a.type.label(), (int) a.experience));
                sb.append('\n').append(I18n.tr("info.army.morale", (int) a.morale));
                if (a.general() != null) sb.append('\n').append(I18n.tr("info.army.general", a.general(), a.generalSkill()));
            }
            if (a.group != null) sb.append('\n').append(I18n.tr("info.group", a.group.displayName()));
        }
        return sb.toString();
    }

    private static Province lastOf(Army a) {
        Province last = null;
        for (Province p : a.path) last = p;
        return last;
    }

    // ---------------------------------------------------------------- widgets

    private static void set(JTextArea area, String text) {
        if (!area.getText().equals(text)) area.setText(text);
    }

    private static JTextArea textArea(int rows) {
        JTextArea t = new JTextArea(rows, 24);
        t.setEditable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return t;
    }

    /** Always as wide as the scroll viewport, so wide buttons are squeezed instead of clipped. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JScrollPane scroll(JTextArea t, int height) {
        JScrollPane sp = new JScrollPane(t);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setPreferredSize(new Dimension(300, height));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return sp;
    }

    private static JButton button() {
        JButton b = new JButton();
        b.setFocusable(false);
        return b;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JButton || c instanceof JComboBox) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return c;
    }
}
