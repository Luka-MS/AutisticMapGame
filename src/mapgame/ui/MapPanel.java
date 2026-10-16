package mapgame.ui;

import static mapgame.model.World.H;
import static mapgame.model.World.W;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Pannable, zoomable map view with province/army selection and move orders. */
public final class MapPanel extends JPanel {
    public enum Mode {
        POLITICAL("Political"), TERRAIN("Terrain");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private record ArmyBox(Army army, Rectangle rect) {
    }

    private static final Color SEA = new Color(0x1B3A57);

    private Game game;
    private BufferedImage political, terrain;
    private double zoom = 1, offX, offY;
    private Mode mode = Mode.POLITICAL;
    private Province selected, hovered;
    private Army selectedArmy;
    private ArmyGroup shownGroup;
    private Runnable selectionListener = () -> { };
    private final List<ArmyBox> boxes = new ArrayList<>();
    private final Highlight selectionHl = new Highlight(0x66FFFFFF), hoverHl = new Highlight(0x30FFFFFF);

    private int pressX, pressY, lastX, lastY;
    private boolean dragged;

    public MapPanel() {
        setBackground(SEA);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                pressX = lastX = e.getX();
                pressY = lastY = e.getY();
                dragged = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) && !SwingUtilities.isMiddleMouseButton(e)) return;
                if (Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 4) dragged = true;
                if (dragged) {
                    offX += e.getX() - lastX;
                    offY += e.getY() - lastY;
                    clampView();
                    repaint();
                }
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (game == null || dragged) return;
                if (SwingUtilities.isLeftMouseButton(e)) click(e.getX(), e.getY());
                else if (SwingUtilities.isRightMouseButton(e)) order(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (game == null) return;
                Province p = provinceAt(e.getX(), e.getY());
                if (p != hovered) {
                    hovered = p;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAt(e.getX(), e.getY(), Math.pow(1.18, -e.getPreciseWheelRotation()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    // ---------------------------------------------------------------- public API

    public void setGame(Game g) {
        game = g;
        selected = hovered = null;
        selectedArmy = null;
        political = MapRenderer.newImage();
        shownGroup = null;
        terrain = MapRenderer.newImage();
        MapRenderer.render(g.world, terrain, false);
        g.mapDirty = true;
        fitToView();
        selectionListener.run();
        repaint();
    }

    public void setSelectionListener(Runnable r) {
        selectionListener = r;
    }

    public Province selectedProvince() {
        return selected;
    }

    public Army selectedArmy() {
        return selectedArmy;
    }

    public void setShownGroup(ArmyGroup g) {
        shownGroup = g;
        repaint();
    }

    public void selectArmy(Army a) {
        selectedArmy = a;
        selected = a == null ? null : a.location;
        selectionListener.run();
        repaint();
    }

    public Mode mode() {
        return mode;
    }

    public void toggleMode() {
        mode = mode == Mode.POLITICAL ? Mode.TERRAIN : Mode.POLITICAL;
        repaint();
    }

    public void clearSelection() {
        selected = null;
        selectedArmy = null;
        selectionListener.run();
        repaint();
    }

    public void pan(int dx, int dy) {
        offX += dx;
        offY += dy;
        clampView();
        repaint();
    }

    public void centerOn(Province p) {
        zoom = Math.max(zoom, 2.0);
        offX = getWidth() / 2.0 - p.x * zoom;
        offY = getHeight() / 2.0 - p.y * zoom;
        clampView();
        repaint();
    }

    /** Called once per UI frame: rebuilds the political image when ownership changed. */
    public void frame() {
        if (game == null) return;
        if (game.mapDirty) {
            game.mapDirty = false;
            game.world.recomputeLabels();
            MapRenderer.render(game.world, political, true);
        }
        if (selectedArmy != null && selectedArmy.dead) {
            selectedArmy = null;
            selectionListener.run();
        }
        repaint();
    }

    // ---------------------------------------------------------------- view helpers

    private void fitToView() {
        if (getWidth() == 0) {
            zoom = 0.6;
            offX = offY = 0;
            return;
        }
        zoom = Math.min(getWidth() / (double) W, getHeight() / (double) H);
        offX = (getWidth() - W * zoom) / 2;
        offY = (getHeight() - H * zoom) / 2;
    }

    private double minZoom() {
        return Math.min(getWidth() / (double) W, getHeight() / (double) H) * 0.9;
    }

    private void zoomAt(int sx, int sy, double factor) {
        double nz = Math.max(minZoom(), Math.min(10, zoom * factor));
        double wx = (sx - offX) / zoom, wy = (sy - offY) / zoom;
        zoom = nz;
        offX = sx - wx * zoom;
        offY = sy - wy * zoom;
        clampView();
        repaint();
    }

    private void clampView() {
        int margin = 120;
        offX = Math.max(margin - W * zoom, Math.min(getWidth() - margin, offX));
        offY = Math.max(margin - H * zoom, Math.min(getHeight() - margin, offY));
    }

    private Province provinceAt(int sx, int sy) {
        return game.world.provinceAt((sx - offX) / zoom, (sy - offY) / zoom);
    }

    // ---------------------------------------------------------------- input

    private void click(int sx, int sy) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            ArmyBox b = boxes.get(i);
            if (b.rect.contains(sx, sy)) {
                selectedArmy = b.army;
                selected = b.army.location;
                selectionListener.run();
                repaint();
                return;
            }
        }
        selected = provinceAt(sx, sy);
        selectedArmy = null;
        if (selected != null && game.player != null) {
            for (Army a : game.armies) {
                if (a.owner == game.player && a.location == selected && !a.dead) {
                    selectedArmy = a;
                    break;
                }
            }
        }
        selectionListener.run();
        repaint();
    }

    private void order(int sx, int sy) {
        if (selectedArmy == null || selectedArmy.owner != game.player) return;
        Province dest = provinceAt(sx, sy);
        if (dest == null) return;
        if (!game.orderMove(selectedArmy, dest)) game.log("Our army cannot reach " + dest.name + ".");
        repaint();
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (game == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                zoom < 1 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(mode == Mode.POLITICAL ? political : terrain, new AffineTransform(zoom, 0, 0, zoom, offX, offY), null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawFerries(g);

        if (hovered != null && hovered != selected) hoverHl.draw(g, game, hovered, zoom, offX, offY);
        if (selected != null) selectionHl.draw(g, game, selected, zoom, offX, offY);
        if (mode == Mode.POLITICAL) drawCountryLabels(g);
        drawGroupFront(g);
        drawArmyPath(g);
        drawArmies(g);
        g.dispose();
    }

    private void drawCountryLabels(Graphics2D g) {
        for (Country c : game.world.countries) {
            if (!c.alive || c.provinces.isEmpty()) continue;
            float size = (float) Math.min(44, Math.sqrt(c.provinces.size()) * zoom * 4.5);
            if (size < 9) continue;
            g.setFont(getFont().deriveFont(Font.BOLD, size));
            FontMetrics fm = g.getFontMetrics();
            int x = (int) (c.labelX * zoom + offX) - fm.stringWidth(c.name) / 2;
            int y = (int) (c.labelY * zoom + offY) + fm.getAscent() / 3;
            g.setColor(new Color(0, 0, 0, 150));
            g.drawString(c.name, x + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(c.name, x, y);
        }
    }

    private double[] armyPos(Army a) {
        double x = a.location.x, y = a.location.y;
        Province next = a.path.peek();
        if (next != null && a.progress > 0) {
            double t = Math.min(1, a.progress / next.travelDays(a.location));
            x += (next.x - x) * t;
            y += (next.y - y) * t;
        }
        return new double[] {x, y};
    }

    /** Dotted lines for sea crossings between islands and the mainland. */
    private void drawFerries(Graphics2D g) {
        if (game.world.ferries.isEmpty()) return;
        g.setColor(new Color(255, 255, 255, 140));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {2f, 5f}, 0f));
        for (Province[] f : game.world.ferries) {
            g.drawLine((int) (f[0].x * zoom + offX), (int) (f[0].y * zoom + offY),
                    (int) (f[1].x * zoom + offX), (int) (f[1].y * zoom + offY));
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawGroupFront(Graphics2D g) {
        ArmyGroup grp = shownGroup;
        if (grp == null) return;
        boolean attack = grp.stance == ArmyGroup.Stance.ATTACK;
        Color base = attack ? new Color(235, 60, 50) : new Color(60, 130, 235);
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 150));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {5f, 5f}, 0f));
        for (Army a : grp.armies) {
            Province slot = grp.slots.get(a);
            if (a.dead || slot == null) continue;
            double[] p = armyPos(a);
            g.drawLine((int) (p[0] * zoom + offX), (int) (p[1] * zoom + offY),
                    (int) (slot.x * zoom + offX), (int) (slot.y * zoom + offY));
        }
        g.setColor(base);
        g.setStroke(new BasicStroke(3f));
        for (Province p : grp.front) {
            int x = (int) (p.x * zoom + offX), y = (int) (p.y * zoom + offY);
            g.drawOval(x - 11, y - 11, 22, 22);
            g.drawLine(x - 6, y, x + 6, y);
            g.drawLine(x, y - 6, x, y + 6);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawArmyPath(Graphics2D g) {
        Army a = selectedArmy;
        if (a == null || a.path.isEmpty()) return;
        g.setColor(new Color(255, 230, 60, 230));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {6f, 5f}, 0f));
        double[] p0 = armyPos(a);
        double px = p0[0] * zoom + offX, py = p0[1] * zoom + offY;
        for (Province p : a.path) {
            double nx = p.x * zoom + offX, ny = p.y * zoom + offY;
            g.drawLine((int) px, (int) py, (int) nx, (int) ny);
            px = nx;
            py = ny;
        }
        g.fillOval((int) px - 4, (int) py - 4, 8, 8);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawArmies(Graphics2D g) {
        boxes.clear();
        double s = Math.max(0.8, Math.min(1.4, zoom));
        int w = (int) (42 * s), h = (int) (20 * s);
        Font font = getFont().deriveFont(Font.BOLD, (float) (11 * s));
        Font tagFont = getFont().deriveFont(Font.BOLD, (float) (10 * s));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        Map<Integer, Integer> stacks = new HashMap<>();
        for (Army a : game.armies) {
            if (a.dead) continue;
            double[] pos = armyPos(a);
            int sx = (int) (pos[0] * zoom + offX) - w / 2, sy = (int) (pos[1] * zoom + offY) - h / 2;
            if (a.progress == 0) sy -= (h + 2) * stacks.merge(a.location.id, 1, Integer::sum) - (h + 2);
            if (sx > getWidth() || sy > getHeight() || sx + w < 0 || sy + h < 0) continue;
            Rectangle r = new Rectangle(sx, sy, w, h);
            boxes.add(new ArmyBox(a, r));
            g.setColor(new Color(a.owner.rgb));
            g.fillRect(r.x, r.y, r.width, r.height);
            boolean sel = a == selectedArmy;
            g.setColor(sel ? Color.YELLOW : Color.BLACK);
            g.setStroke(new BasicStroke(sel ? 2.5f : 1.2f));
            g.drawRect(r.x, r.y, r.width, r.height);
            g.setStroke(new BasicStroke(1f));
            String text = formatStrength(a.strength);
            int lum = (((a.owner.rgb >> 16) & 0xFF) * 3 + ((a.owner.rgb >> 8) & 0xFF) * 6 + (a.owner.rgb & 0xFF)) / 10;
            g.setColor(lum > 140 ? Color.BLACK : Color.WHITE);
            g.drawString(text, r.x + (w - fm.stringWidth(text)) / 2, r.y + (h + fm.getAscent()) / 2 - 1);
            if (a.group != null) {
                g.setFont(tagFont);
                String tag = "G" + a.group.id;
                g.setColor(Color.BLACK);
                g.drawString(tag, r.x + 2, r.y - 1);
                g.setColor(Color.WHITE);
                g.drawString(tag, r.x + 1, r.y - 2);
                g.setFont(font);
            }
        }
    }

    public static String formatStrength(int n) {
        return n >= 1000 ? String.format("%.1fk", n / 1000.0) : Integer.toString(n);
    }

    /** Cached translucent overlay covering one province. */
    private static final class Highlight {
        private final int argb;
        private Province cached;
        private BufferedImage img;

        Highlight(int argb) {
            this.argb = argb;
        }

        void draw(Graphics2D g, Game game, Province p, double zoom, double offX, double offY) {
            if (cached != p) {
                int w = p.maxX - p.minX + 1, h = p.maxY - p.minY + 1;
                img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (game.world.ids[(p.minY + y) * W + p.minX + x] == p.id) img.setRGB(x, y, argb);
                    }
                }
                cached = p;
            }
            g.drawImage(img, new AffineTransform(zoom, 0, 0, zoom, offX + p.minX * zoom, offY + p.minY * zoom), null);
        }
    }
}
