package mapgame.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import mapgame.i18n.I18n;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.sim.Game;
import mapgame.sim.Stats;

/** Line charts of land, people, army, navy, gold and income over time for any selection of nations. */
final class StatisticsTab implements NationTab {
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final JComboBox<Stats.Metric> metricBox = new JComboBox<>(Stats.Metric.values());
    private final JButton topBtn = new JButton(), meBtn = new JButton();
    private final DefaultListModel<Country> model = new DefaultListModel<>();
    private final JList<Country> list = new JList<>(model);
    private final JLabel hint = new JLabel();
    private final Chart chart = new Chart();
    private Game game;
    private int lastSize = -1;
    private boolean settingSelection;

    StatisticsTab() {
        metricBox.setFocusable(false);
        metricBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, ((Stats.Metric) v).label(), i, sel, focus);
            }
        });
        metricBox.addActionListener(e -> chart.repaint());
        topBtn.setFocusable(false);
        meBtn.setFocusable(false);
        topBtn.addActionListener(e -> selectTop(8));
        meBtn.addActionListener(e -> selectMe());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                Country c = (Country) v;
                JLabel label = (JLabel) super.getListCellRendererComponent(l, c.displayName() + (c.alive ? "" : " \u2020"), i, sel, focus);
                label.setIcon(new Swatch(new Color(c.rgb)));
                return label;
            }
        });
        list.addListSelectionListener(e -> {
            if (!settingSelection && !e.getValueIsAdjusting()) chart.repaint();
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(metricBox);
        top.add(topBtn);
        top.add(meBtn);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(170, 100));
        JPanel south = new JPanel(new BorderLayout());
        south.add(hint, BorderLayout.CENTER);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(chart, BorderLayout.CENTER);
        root.add(scroll, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
    }

    @Override
    public String titleKey() {
        return "tab.stats";
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public void applyLanguage() {
        topBtn.setText(I18n.tr("stats.top"));
        meBtn.setText(I18n.tr("stats.me"));
        hint.setText(I18n.tr("stats.hint"));
        metricBox.repaint();
        list.repaint();
        chart.repaint();
    }

    @Override
    public void refresh(Game g, Province p) {
        if (g != game) {
            game = g;
            model.clear();
            List<Country> all = new ArrayList<>(g.world.countries);
            all.sort(Comparator.comparing(Country::displayName));
            for (Country c : all) model.addElement(c);
            lastSize = -1;
            selectTop(6);
        }
        if (g.stats.size() != lastSize) {
            lastSize = g.stats.size();
            chart.repaint();
        }
    }

    private Stats.Metric metric() {
        return (Stats.Metric) metricBox.getSelectedItem();
    }

    /** The player's nation plus the biggest ones by the current metric. */
    private void selectTop(int count) {
        if (game == null) return;
        Stats s = game.stats;
        int last = s.size() - 1;
        List<Country> ranked = new ArrayList<>();
        for (Country c : game.world.countries) if (c.alive) ranked.add(c);
        ranked.sort((a, b) -> Integer.compare(s.value(last, b, metric()), s.value(last, a, metric())));
        List<Country> pick = new ArrayList<>();
        if (game.player != null) pick.add(game.player);
        for (Country c : ranked) {
            if (pick.size() >= count) break;
            if (!pick.contains(c)) pick.add(c);
        }
        select(pick);
    }

    private void selectMe() {
        if (game != null && game.player != null) select(List.of(game.player));
    }

    private void select(List<Country> countries) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) if (countries.contains(model.get(i))) idx.add(i);
        settingSelection = true;
        list.setSelectedIndices(idx.stream().mapToInt(Integer::intValue).toArray());
        settingSelection = false;
        chart.repaint();
    }

    private static String format(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format("%.1fG", v / 1e9);
        if (a >= 1e6) return String.format("%.1fM", v / 1e6);
        if (a >= 10_000) return String.format("%.0fk", v / 1e3);
        if (a >= 1000) return String.format("%.1fk", v / 1e3);
        return String.format("%.0f", v);
    }

    /** Rounds up to a 1-2-5 step so the axis gets pleasant tick values. */
    static double niceStep(double range, int ticks) {
        double raw = Math.max(range, 1e-9) / ticks;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        double step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return step * mag;
    }

    /** A small coloured square in front of the nation's name. */
    private record Swatch(Color color) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 1, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y + 1, 12, 12);
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    private final class Chart extends JPanel {
        private int hoverX = -1;

        Chart() {
            setPreferredSize(new Dimension(420, 300));
            setBackground(new Color(0x16263A));
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = -1;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = g.getFontMetrics();
            List<Country> shown = list.getSelectedValuesList();
            Stats s = game == null ? null : game.stats;
            if (s == null || s.size() < 2 || shown.isEmpty()) {
                g.setColor(new Color(0x9FB3C8));
                String msg = I18n.tr(s == null || s.size() < 2 ? "stats.none" : "stats.noselection");
                g.drawString(msg, 20, 30);
                g.dispose();
                return;
            }
            Stats.Metric m = metric();
            double min = 0, max = 1;
            for (Country c : shown) {
                for (int v : s.series(c, m)) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            double step = niceStep(max - min, 5);
            max = Math.ceil(max / step) * step;
            min = Math.floor(min / step) * step;
            int left = 56, right = 12, top = 12, bottom = 26;
            int w = getWidth() - left - right, h = getHeight() - top - bottom;
            double x0 = s.day(0), x1 = s.day(s.size() - 1);
            g.setStroke(new BasicStroke(1f));
            // Horizontal grid with value labels.
            for (double v = min; v <= max + step / 2; v += step) {
                int y = (int) (top + h - (v - min) / (max - min) * h);
                g.setColor(new Color(255, 255, 255, 40));
                g.drawLine(left, y, left + w, y);
                g.setColor(new Color(0xC8D6E5));
                String t = format(v);
                g.drawString(t, left - 6 - fm.stringWidth(t), y + fm.getAscent() / 2 - 1);
            }
            // Vertical grid with years.
            int firstYear = 1900 + (int) (x0 / 360), lastYear = 1900 + (int) (x1 / 360);
            int yearStep = Math.max(1, (int) Math.ceil((lastYear - firstYear) / Math.max(1.0, w / 70.0)));
            for (int year = firstYear; year <= lastYear; year += yearStep) {
                double day = (year - 1900) * 360.0;
                if (day < x0) continue;
                int x = (int) (left + (day - x0) / (x1 - x0) * w);
                g.setColor(new Color(255, 255, 255, 25));
                g.drawLine(x, top, x, top + h);
                g.setColor(new Color(0xC8D6E5));
                String t = Integer.toString(year);
                g.drawString(t, x - fm.stringWidth(t) / 2, top + h + fm.getAscent() + 4);
            }
            // The lines: the player's nation is drawn thicker.
            for (Country c : shown) {
                boolean me = c == game.player;
                g.setStroke(new BasicStroke(me ? 3.2f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(visible(new Color(c.rgb)));
                int px = 0, py = 0;
                for (int i = 0; i < s.size(); i++) {
                    int x = (int) (left + (s.day(i) - x0) / (x1 - x0) * w);
                    int y = (int) (top + h - (s.value(i, c, m) - min) / (max - min) * h);
                    if (i > 0) g.drawLine(px, py, x, y);
                    px = x;
                    py = y;
                }
            }
            // Hover: values at the mouse position.
            if (hoverX >= left && hoverX <= left + w) {
                double day = x0 + (hoverX - left) / (double) w * (x1 - x0);
                int idx = 0;
                for (int i = 0; i < s.size(); i++) if (Math.abs(s.day(i) - day) < Math.abs(s.day(idx) - day)) idx = i;
                int x = (int) (left + (s.day(idx) - x0) / (x1 - x0) * w);
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(255, 255, 255, 120));
                g.drawLine(x, top, x, top + h);
                List<Country> sorted = new ArrayList<>(shown);
                final int at = idx;
                sorted.sort((a, b) -> Integer.compare(s.value(at, b, m), s.value(at, a, m)));
                int lineH = fm.getHeight();
                String head = Game.formatDate(s.day(idx));
                int boxW = fm.stringWidth(head);
                List<String> lines = new ArrayList<>();
                for (Country c : sorted) {
                    String t = c.displayName() + ": " + format(s.value(idx, c, m));
                    lines.add(t);
                    boxW = Math.max(boxW, fm.stringWidth(t) + 16);
                }
                int boxH = lineH * (lines.size() + 1) + 8;
                int bx = x + 10 + boxW > left + w ? x - 10 - boxW : x + 10;
                g.setColor(new Color(10, 20, 35, 225));
                g.fillRoundRect(bx, top + 4, boxW + 8, boxH, 8, 8);
                g.setColor(Color.WHITE);
                g.drawString(head, bx + 4, top + 4 + lineH);
                int row = 2;
                for (int i = 0; i < lines.size(); i++, row++) {
                    g.setColor(visible(new Color(sorted.get(i).rgb)));
                    g.fillRect(bx + 4, top + 4 + lineH * row - fm.getAscent() + 3, 8, 8);
                    g.setColor(Color.WHITE);
                    g.drawString(lines.get(i), bx + 16, top + 4 + lineH * row);
                }
            }
            g.dispose();
        }

        /** Dark nation colours are brightened so they show up on the dark chart. */
        private Color visible(Color c) {
            double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            if (lum >= 90) return c;
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            return Color.getHSBColor(hsb[0], Math.min(1f, hsb[1]), 0.8f);
        }
    }
}
