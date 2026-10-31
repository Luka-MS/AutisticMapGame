package mapgame.ui;

import static mapgame.model.World.H;
import static mapgame.model.World.W;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import mapgame.i18n.I18n;
import mapgame.model.Army;
import mapgame.model.ArmyGroup;
import mapgame.model.ArmyType;
import mapgame.model.Country;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Pannable, zoomable map view with province/army selection and move orders. */
public final class MapPanel extends JPanel {
    public enum Mode {
        POLITICAL, TERRAIN;

        public String label() {
            return I18n.tr("mapmode." + name());
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
    private final Set<Army> armySel = new LinkedHashSet<>();
    private ArmyGroup shownGroup;
    private Runnable selectionListener = () -> { };
    private final List<ArmyBox> boxes = new ArrayList<>();
    private final Highlight selectionHl = new Highlight(0x66FFFFFF), hoverHl = new Highlight(0x30FFFFFF);

    private int pressX, pressY, lastX, lastY;
    private boolean dragged, boxing, shiftAtPress, miniDrag;
    private static final Color BOX_FILL = new Color(255, 255, 255, 40), BOX_LINE = new Color(255, 255, 255, 220);

    private static final int MINI_W = 240, MINI_H = 135, MINI_MARGIN = 10;
    private BufferedImage miniPolitical, miniTerrain;

    public MapPanel() {
        setBackground(SEA);
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(350);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                pressX = lastX = e.getX();
                pressY = lastY = e.getY();
                dragged = false;
                boxing = false;
                shiftAtPress = e.isShiftDown();
                miniDrag = game != null && SwingUtilities.isLeftMouseButton(e) && miniRect().contains(e.getPoint());
                if (miniDrag) centerFromMinimap(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (miniDrag) {
                    centerFromMinimap(e.getX(), e.getY());
                    return;
                }
                boolean left = SwingUtilities.isLeftMouseButton(e);
                if (!left && !SwingUtilities.isRightMouseButton(e) && !SwingUtilities.isMiddleMouseButton(e)) return;
                if (Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 4) dragged = true;
                // Shift + left-drag draws the selection box; every other drag moves the camera.
                if (left && shiftAtPress) {
                    boxing = dragged && game != null;
                    lastX = e.getX();
                    lastY = e.getY();
                    if (boxing) repaint();
                    return;
                }
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
                if (game == null) return;
                if (miniDrag) {
                    miniDrag = false;
                    return;
                }
                if (boxing) {
                    boxing = false;
                    boxSelect();
                    return;
                }
                if (dragged) return;
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
        setArmySelection(null);
        political = MapRenderer.newImage();
        shownGroup = null;
        terrain = MapRenderer.newImage();
        MapRenderer.render(g.world, terrain, false);
        miniTerrain = shrink(terrain);
        miniPolitical = null;
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

    /** All selected armies; the first one is {@link #selectedArmy()}. */
    public List<Army> selectedArmies() {
        return new ArrayList<>(armySel);
    }

    public void setShownGroup(ArmyGroup g) {
        shownGroup = g;
        repaint();
    }

    public void selectArmy(Army a) {
        setArmySelection(a == null ? null : List.of(a));
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
        setArmySelection(null);
        selectionListener.run();
        repaint();
    }

    private void setArmySelection(Collection<Army> armies) {
        armySel.clear();
        if (armies != null) armySel.addAll(armies);
        selectedArmy = armySel.isEmpty() ? null : armySel.iterator().next();
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
            miniPolitical = shrink(political);
        }
        if (armySel.removeIf(a -> a.dead)) {
            selectedArmy = armySel.isEmpty() ? null : armySel.iterator().next();
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
                setArmySelection(List.of(b.army));
                selected = b.army.location;
                selectionListener.run();
                repaint();
                return;
            }
        }
        selected = provinceAt(sx, sy);
        setArmySelection(null);
        if (selected != null && game.player != null) {
            for (Army a : game.armies) {
                if (a.owner == game.player && a.location == selected && !a.dead) {
                    setArmySelection(List.of(a));
                    break;
                }
            }
        }
        selectionListener.run();
        repaint();
    }

    /** Selects all own armies whose counters touch the dragged rectangle. */
    private void boxSelect() {
        Rectangle area = new Rectangle(Math.min(pressX, lastX), Math.min(pressY, lastY),
                Math.abs(lastX - pressX), Math.abs(lastY - pressY));
        List<Army> hit = new ArrayList<>();
        for (ArmyBox b : boxes) {
            if (b.army.owner == game.player && !b.army.dead && b.rect.intersects(area) && !hit.contains(b.army)) hit.add(b.army);
        }
        setArmySelection(hit);
        if (selectedArmy != null) selected = selectedArmy.location;
        selectionListener.run();
        repaint();
    }

    private void order(int sx, int sy) {
        if (selectedArmy == null || selectedArmy.owner != game.player) return;
        Province dest = provinceAt(sx, sy);
        if (dest == null) return;
        int failed = 0;
        for (Army a : armySel) {
            if (a.owner == game.player && !a.dead && !game.orderMove(a, dest)) failed++;
        }
        if (failed == 1 && armySel.size() == 1) game.log(I18n.tr("army.cannot.reach", dest.name));
        else if (failed > 0) game.log(I18n.tr("army.cannot.reach.n", failed, armySel.size(), dest.name));
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
        drawCities(g);
        drawGroupFront(g);
        drawArmyPath(g);
        drawArmies(g);
        drawMinimap(g);
        if (boxing) {
            int x = Math.min(pressX, lastX), y = Math.min(pressY, lastY);
            int w = Math.abs(lastX - pressX), h = Math.abs(lastY - pressY);
            g.setColor(BOX_FILL);
            g.fillRect(x, y, w, h);
            g.setColor(BOX_LINE);
            g.drawRect(x, y, w, h);
        }
        g.dispose();
    }

    /** Capitals (square) and other big cities (dot); names appear when zoomed in. */
    private void drawCities(Graphics2D g) {
        if (zoom < 0.9) return;
        boolean names = zoom >= 1.8;
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        for (Province p : game.world.provinces) {
            boolean cap = p.isCapital();
            if (p.city == null && !cap) continue;
            int x = (int) (p.x * zoom + offX), y = (int) (p.y * zoom + offY);
            if (x < -60 || y < -20 || x > getWidth() + 60 || y > getHeight() + 20) continue;
            int s = cap ? 7 : 5;
            g.setColor(Color.BLACK);
            g.fillRect(x - s / 2 - 1, y - s / 2 - 1, s + 2, s + 2);
            g.setColor(cap ? new Color(255, 215, 60) : Color.WHITE);
            if (cap) g.fillRect(x - s / 2, y - s / 2, s, s);
            else g.fillOval(x - s / 2, y - s / 2, s, s);
            if (names || cap && zoom >= 1.3) {
                String label = p.city != null ? p.city : p.name;
                g.setColor(new Color(0, 0, 0, 170));
                g.drawString(label, x + s + 1, y + fm.getAscent() / 2 + 1);
                g.setColor(Color.WHITE);
                g.drawString(label, x + s, y + fm.getAscent() / 2);
            }
        }
    }

    // ---------------------------------------------------------------- minimap and tooltip

    private Rectangle miniRect() {
        return new Rectangle(MINI_MARGIN, getHeight() - MINI_H - MINI_MARGIN, MINI_W, MINI_H);
    }

    private static BufferedImage shrink(BufferedImage src) {
        BufferedImage out = new BufferedImage(MINI_W, MINI_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getScaledInstance(MINI_W, MINI_H, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return out;
    }

    private void centerFromMinimap(int sx, int sy) {
        Rectangle r = miniRect();
        double wx = (sx - r.x) / (double) MINI_W * W, wy = (sy - r.y) / (double) MINI_H * H;
        offX = getWidth() / 2.0 - wx * zoom;
        offY = getHeight() / 2.0 - wy * zoom;
        clampView();
        repaint();
    }

    private void drawMinimap(Graphics2D g) {
        BufferedImage img = mode == Mode.POLITICAL && miniPolitical != null ? miniPolitical : miniTerrain;
        if (img == null || getWidth() < MINI_W * 2 || getHeight() < MINI_H * 2) return;
        Rectangle r = miniRect();
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
        g.drawImage(img, r.x, r.y, null);
        double sx = MINI_W / (double) W, sy = MINI_H / (double) H;
        int vx = r.x + (int) Math.round(-offX / zoom * sx), vy = r.y + (int) Math.round(-offY / zoom * sy);
        int vw = (int) Math.round(getWidth() / zoom * sx), vh = (int) Math.round(getHeight() / zoom * sy);
        Shape oldClip = g.getClip();
        g.clipRect(r.x, r.y, r.width, r.height);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(vx, vy, vw, vh);
        g.setStroke(new BasicStroke(1f));
        g.setClip(oldClip);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (game == null || miniRect().contains(e.getPoint()) || boxing) return null;
        Province p = provinceAt(e.getX(), e.getY());
        if (p == null) return null;
        StringBuilder sb = new StringBuilder("<html><b>").append(html(p.name)).append("</b> (").append(html(p.terrain.label())).append(')');
        if (p.isLand()) {
            sb.append("<br>").append(html(I18n.tr("info.owner", p.owner == null ? I18n.tr("info.unclaimed") : p.owner.displayName())));
            if (p.owner != null && p.controller != p.owner) {
                sb.append("<br>").append(html(I18n.tr("info.occupied", p.controller.displayName())));
            }
            sb.append("<br>").append(html(I18n.tr("info.population", p.population, p.income(game.day))).replace("\n", "<br>"));
            if (p.city != null) sb.append("<br>").append(html(I18n.tr("info.city", p.city, p.cityPopulation)));
            if (p.isCapital()) sb.append("<br><b>").append(html(I18n.tr("info.capital", p.owner.displayName()))).append("</b>");
            Map<Country, Integer> here = new LinkedHashMap<>();
            for (Army a : game.armies) if (!a.dead && a.location == p) here.merge(a.owner, a.strength, Integer::sum);
            if (!here.isEmpty()) {
                StringBuilder list = new StringBuilder();
                for (Map.Entry<Country, Integer> en : here.entrySet()) {
                    list.append(list.length() > 0 ? ", " : "").append(en.getKey().displayName()).append(' ')
                            .append(String.format(I18n.locale(), "%,d", en.getValue()));
                }
                sb.append("<br>").append(html(I18n.tr("tip.armies", list)));
            }
        }
        return sb.append("</html>").toString();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Selects a province and brings it to the centre of the view (used by the notification list). */
    public void showProvince(Province p) {
        if (p == null || game == null) return;
        selected = p;
        setArmySelection(null);
        selectionListener.run();
        centerOn(p);
    }

    private void drawCountryLabels(Graphics2D g) {
        for (Country c : game.world.countries) {
            if (!c.alive || c.provinces.isEmpty()) continue;
            float size = (float) Math.min(44, Math.sqrt(c.provinces.size()) * zoom * 4.5);
            if (size < 9) continue;
            g.setFont(getFont().deriveFont(Font.BOLD, size));
            FontMetrics fm = g.getFontMetrics();
            int x = (int) (c.labelX * zoom + offX) - fm.stringWidth(c.displayName()) / 2;
            int y = (int) (c.labelY * zoom + offY) + fm.getAscent() / 3;
            g.setColor(new Color(0, 0, 0, 150));
            g.drawString(c.displayName(), x + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(c.displayName(), x, y);
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
        g.setColor(new Color(255, 230, 60, 230));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {6f, 5f}, 0f));
        for (Army a : armySel) drawPath(g, a);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawPath(Graphics2D g, Army a) {
        if (a.path.isEmpty()) return;
        double[] p0 = armyPos(a);
        double px = p0[0] * zoom + offX, py = p0[1] * zoom + offY;
        for (Province p : a.path) {
            double nx = p.x * zoom + offX, ny = p.y * zoom + offY;
            g.drawLine((int) px, (int) py, (int) nx, (int) ny);
            px = nx;
            py = ny;
        }
        g.fillOval((int) px - 4, (int) py - 4, 8, 8);
    }

    private void drawArmies(Graphics2D g) {
        boxes.clear();
        double s = Math.max(0.8, Math.min(1.4, zoom));
        int w = (int) (50 * s), h = (int) (20 * s);
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
            boolean sel = armySel.contains(a);
            g.setColor(sel ? Color.YELLOW : Color.BLACK);
            g.setStroke(new BasicStroke(sel ? 2.5f : 1.2f));
            g.drawRect(r.x, r.y, r.width, r.height);
            g.setStroke(new BasicStroke(1f));
            String text = (a.type == ArmyType.INFANTRY ? "" : a.type.tag + " ") + formatStrength(a.strength);
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
