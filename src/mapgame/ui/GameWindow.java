package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import mapgame.model.Army;
import mapgame.model.Country;
import mapgame.model.MapKind;
import mapgame.model.Province;
import mapgame.sim.Game;

public final class GameWindow extends JFrame {
    /** Real milliseconds per in-game day for speeds 1..5 (index 0 = paused). */
    private static final int[] DAY_MS = {0, 1000, 500, 250, 120, 40};

    private final MapPanel map = new MapPanel();
    private final JLabel dateLabel = new JLabel();
    private final JTextArea status = textArea(5), info = textArea(8), logArea = new JTextArea();
    private final JButton startBtn = button("Start as selected nation"), recruitBtn = button("Recruit army (40 gold, 1000 men)"),
            splitBtn = button("Split army in half (X)"), mergeBtn = button("Merge armies here (C)"),
            groupsBtn = button("Army groups / fronts (G)"),
            warBtn = button("Declare war"), totalWarBtn = button("Declare TOTAL war (no peace)"),
            peaceBtn = button("Propose peace"), menuBtn = button("Main menu"),
            modeBtn = button("Map mode: Political (M)");
    private final List<JToggleButton> speedBtns = new ArrayList<>();
    private JSplitPane sideSplit;
    private final ArmyGroupDialog groupDialog;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final MainMenuPanel menu;

    private Game game;
    private boolean started;
    private boolean inMenu = true;
    private boolean dividerPlaced;
    private int speed;
    private double acc;
    private long lastNanos = System.nanoTime();
    private long lastHud;

    public GameWindow() {
        super("Map Game");
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
        map.setSelectionListener(this::refresh);
        setSize(1380, 820);
        setLocationRelativeTo(null);
        groupDialog = new ArmyGroupDialog(this, map);

        startBtn.addActionListener(e -> startGame());
        menuBtn.addActionListener(e -> showMenu());
        modeBtn.addActionListener(e -> toggleMode());
        groupsBtn.addActionListener(e -> showGroups());
        recruitBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p != null && game.recruit(game.player, p) == null) game.log("Cannot recruit there.");
            refresh();
        });
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
            int answer = JOptionPane.showConfirmDialog(this,
                    "A total war against " + p.owner.name + " can NEVER end in peace.\n"
                            + "It only ends when one of the two nations is destroyed.\n\nDeclare total war?",
                    "Total war", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) game.declareWar(game.player, p.owner, true);
            refresh();
        });
        peaceBtn.addActionListener(e -> {
            Province p = map.selectedProvince();
            if (p != null && p.owner != null) game.log(game.proposePeace(game.player, p.owner));
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
        menu.setBusy("Generating " + kind.label + "...");
        new SwingWorker<Game, Void>() {
            @Override
            protected Game doInBackground() {
                return Game.create(kind, seed);
            }

            @Override
            protected void done() {
                try {
                    installGame(get());
                } catch (Exception ex) {
                    menu.setBusy(null);
                    JOptionPane.showMessageDialog(GameWindow.this, "Could not create the world:\n" + ex.getCause(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void installGame(Game g) {
        game = g;
        game.logSink = s -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        };
        started = false;
        acc = 0;
        logArea.setText("");
        setSpeed(0);
        groupDialog.bind(game);
        showGame();
        map.setGame(game);
        logArea.append("Pick a nation: click any coloured province, then press Start.\n");
        logArea.append("Left-drag: pan. Wheel: zoom. Right-click: move selected army.\n");
        refresh();
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
        if (n == null) game.log("This army cannot be split right now.");
        else map.selectArmy(n);
        refresh();
    }

    private void mergeSelected() {
        Army a = map.selectedArmy();
        if (!playerArmy(a)) return;
        if (game.mergeStack(a) == 0) game.log("No idle armies to merge here.");
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
        speeds.setLayout(new BoxLayout(speeds, BoxLayout.X_AXIS));
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
        head.add(Box.createVerticalStrut(6));
        head.add(scroll(status, 100));

        JScrollPane infoScroll = new JScrollPane(info);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        for (JButton b : new JButton[] {startBtn, recruitBtn, splitBtn, mergeBtn, groupsBtn, warBtn, totalWarBtn, peaceBtn}) buttons.add(left(b));

        JPanel top = new JPanel(new BorderLayout(0, 6));
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
        bottom.add(new JScrollPane(logArea), BorderLayout.CENTER);
        bottom.add(menuBtn, BorderLayout.SOUTH);
        bottom.setMinimumSize(new Dimension(300, 90));

        // Vertical splitter: dragging the divider up or down resizes the log.
        sideSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topScroll, bottom);
        sideSplit.setResizeWeight(1.0);
        sideSplit.setContinuousLayout(true);
        sideSplit.setDividerSize(8);
        sideSplit.setBorder(null);
        sideSplit.setPreferredSize(new Dimension(340, 0));
        return sideSplit;
    }

    private void installKeys() {
        bind("SPACE", () -> setSpeed(speed == 0 ? 1 : 0));
        for (int i = 1; i <= 5; i++) {
            final int s = i;
            bind(Integer.toString(i), () -> setSpeed(s));
        }
        bind("M", this::toggleMode);
        bind("X", this::splitSelected);
        bind("C", this::mergeSelected);
        bind("G", this::showGroups);
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
        game.log("You lead " + game.player.name + ". Press Space to unpause.");
        map.centerOn(game.player.capital);
        setSpeed(1);
        refresh();
    }

    private void setSpeed(int s) {
        speed = started ? s : 0;
        speedBtns.get(speed).setSelected(true);
    }

    private void toggleMode() {
        map.toggleMode();
        modeBtn.setText("Map mode: " + map.mode().label + " (M)");
    }

    private void frame() {
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1e6;
        lastNanos = now;
        if (inMenu) return;
        if (started && speed > 0) {
            acc += dt;
            int steps = 0;
            while (acc >= DAY_MS[speed] && steps < 10) {
                acc -= DAY_MS[speed];
                game.tickDay();
                steps++;
            }
            if (steps == 10) acc = 0;
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
            JOptionPane.showMessageDialog(this, game.player.name + " has fallen. Your nation is no more.", "Defeat",
                    JOptionPane.WARNING_MESSAGE);
        } else if (game.aliveCountries().size() == 1) {
            started = false;
            setSpeed(0);
            JOptionPane.showMessageDialog(this, "You rule the whole world!", "Victory", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- HUD

    private void refresh() {
        if (game == null) return;
        Country me = game.player;
        Province sel = map.selectedProvince();
        dateLabel.setText(game.date());

        if (started && me != null) {
            double net = game.monthlyIncome(me) - game.monthlyUpkeep(me);
            List<Country> foes = game.enemiesOf(me);
            StringBuilder wars = new StringBuilder();
            for (Country f : foes) wars.append(wars.length() > 0 ? ", " : "").append(f.name);
            set(status, String.format("%s%nGold: %,.0f (%+.1f / month)%nManpower: %,d (+%,d / month)%nProvinces: %d   Army: %,d men%nAt war with: %s",
                    me.name, me.gold, net, me.manpower, game.monthlyManpower(me), me.provinces.size(), game.strength(me),
                    foes.isEmpty() ? "nobody" : wars));
        } else {
            set(status, "Choose your nation.\nClick a coloured province, then press 'Start'.\n\nSpace pauses, 1-5 sets speed.");
        }
        set(info, describe(sel));

        startBtn.setVisible(!started);
        startBtn.setEnabled(sel != null && sel.owner != null);
        boolean playing = started && me != null;
        boolean own = playing && sel != null && sel.owner == me && sel.controller == me;
        boolean foreign = playing && sel != null && sel.owner != null && sel.owner != me;
        recruitBtn.setEnabled(own && me.gold >= Game.RECRUIT_GOLD && me.manpower >= Game.RECRUIT_MEN);
        Army sa = map.selectedArmy();
        boolean mine = playerArmy(sa);
        splitBtn.setEnabled(mine && game.canSplit(sa));
        mergeBtn.setEnabled(mine && game.mergeCandidates(sa) > 0);
        warBtn.setEnabled(foreign && !game.atWar(me, sel.owner));
        boolean atWarWithSel = foreign && game.atWar(me, sel.owner);
        boolean totalWithSel = atWarWithSel && game.isTotalWar(me, sel.owner);
        totalWarBtn.setEnabled(foreign && !totalWithSel);
        totalWarBtn.setText(!foreign ? "Declare TOTAL war (no peace)"
                : atWarWithSel ? "Escalate to TOTAL war on " + sel.owner.name : "Declare TOTAL war on " + sel.owner.name);
        peaceBtn.setEnabled(atWarWithSel && !totalWithSel);
        peaceBtn.setText(totalWithSel ? "No peace possible (total war)" : "Propose peace");
        groupsBtn.setEnabled(playing);
        warBtn.setText(foreign ? "Declare war on " + sel.owner.name : "Declare war");
        if (groupDialog.isVisible()) groupDialog.refresh();
    }

    private String describe(Province p) {
        if (p == null) return "Nothing selected.\n\nRight-click a province to send the\nselected army there.";
        StringBuilder sb = new StringBuilder();
        sb.append(p.name).append("  (").append(p.terrain.label).append(")\n");
        if (!p.isLand()) return sb.append("Open sea.").toString();
        sb.append(String.format("Population: %,d%nIncome: %.1f gold / month%n", p.population, p.income()));
        sb.append("Owner: ").append(p.owner == null ? "unclaimed" : p.owner.name).append('\n');
        if (p.owner != null && p.controller != p.owner) sb.append("Occupied by: ").append(p.controller.name).append('\n');
        if (!p.ferryTo.isEmpty()) sb.append("Sea crossings: +").append(Province.FERRY_DAYS).append(" days\n");
        sb.append(String.format("Travel time: %d days%n", p.terrain.moveDays));
        if (p.owner != null) {
            Country c = p.owner;
            sb.append(String.format("%n%s: %d provinces, %,d men%n", c.name, c.provinces.size(), game.strength(c)));
            if (started && c != game.player) {
                sb.append(game.atWar(game.player, c) ? (game.isTotalWar(game.player, c) ? "TOTAL WAR - no peace (war score " : "AT WAR (war score ")
                        + String.format("%+d", game.warScore(game.player, c)) + ")" : "At peace");
            }
        }
        Army a = map.selectedArmy();
        if (a != null && !a.dead) {
            String state = game.isEngaged(a) ? "in battle" : a.path.isEmpty() ? "idle" : "marching to " + lastOf(a).name;
            sb.append(String.format("%n%nArmy of %s: %,d men, %s", a.owner.name, a.strength, state));
            if (a.group != null) sb.append("\nGroup: ").append(a.group.name);
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
        t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return t;
    }

    private static JScrollPane scroll(JTextArea t, int height) {
        JScrollPane sp = new JScrollPane(t);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setPreferredSize(new Dimension(300, height));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return sp;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        return b;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JButton) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return c;
    }
}
