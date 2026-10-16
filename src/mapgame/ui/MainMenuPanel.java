package mapgame.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import mapgame.model.MapGenerator;
import mapgame.model.MapKind;
import mapgame.model.RealMapGenerator;
import mapgame.model.World;

/** Start screen: pick a random world or a real map. */
public final class MainMenuPanel extends JPanel {
    public interface Listener {
        void play(MapKind kind, long seed);

        void load();

        void resume();

        void quit();
    }

    private static final int THUMB_W = 340, THUMB_H = 191;
    private static final Color PANEL = new Color(0x1E2F42), PANEL_HOVER = new Color(0x2B4560), TEXT = new Color(0xE8EEF4),
            MUTED = new Color(0x9FB3C8), ACCENT = new Color(0xF2C14E);

    private final Listener listener;
    private final Map<MapKind, JLabel> thumbs = new EnumMap<>(MapKind.class);
    private final Map<MapKind, JPanel> cards = new EnumMap<>(MapKind.class);
    private final JButton resumeBtn = new JButton("Resume current game"), quitBtn = new JButton("Quit"),
            loadBtn = new JButton("Load game..."), rerollBtn = new JButton("Re-roll");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private long randomSeed = System.nanoTime();
    private boolean busy;
    private int previewGeneration;

    public MainMenuPanel(Listener listener) {
        this.listener = listener;
        setLayout(new GridBagLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("MAP GAME", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 64f));
        title.setForeground(TEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Choose your world", SwingConstants.CENTER);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 22f));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        content.add(title);
        content.add(subtitle);
        content.add(Box.createVerticalStrut(30));

        JPanel row = new JPanel(new GridLayout(1, 3, 28, 0));
        row.setOpaque(false);
        for (MapKind kind : MapKind.values()) row.add(buildCard(kind));
        row.setAlignmentX(CENTER_ALIGNMENT);
        content.add(row);
        content.add(Box.createVerticalStrut(24));

        statusLabel.setForeground(ACCENT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(12));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        bottom.setOpaque(false);
        resumeBtn.addActionListener(e -> listener.resume());
        loadBtn.addActionListener(e -> listener.load());
        quitBtn.addActionListener(e -> listener.quit());
        for (JButton b : new JButton[] {resumeBtn, loadBtn, quitBtn}) {
            b.setFocusable(false);
            b.setPreferredSize(new Dimension(200, 36));
            bottom.add(b);
        }
        resumeBtn.setVisible(false);
        bottom.setAlignmentX(CENTER_ALIGNMENT);
        content.add(bottom);

        add(content, new GridBagConstraints());
        loadPreviews(MapKind.values());
    }

    private JPanel buildCard(MapKind kind) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3A5573), 2), BorderFactory.createEmptyBorder(12, 12, 14, 12)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel thumb = new JLabel("Preview...", SwingConstants.CENTER);
        thumb.setPreferredSize(new Dimension(THUMB_W, THUMB_H));
        thumb.setOpaque(true);
        thumb.setBackground(new Color(0x1B3A57));
        thumb.setForeground(MUTED);
        thumbs.put(kind, thumb);
        card.add(thumb, BorderLayout.NORTH);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(kind.label);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 26f));
        name.setForeground(TEXT);
        JTextArea desc = new JTextArea(kind.description);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setEditable(false);
        desc.setFocusable(false);
        desc.setOpaque(false);
        desc.setBorder(null);
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 14f));
        desc.setForeground(MUTED);
        desc.setAlignmentX(LEFT_ALIGNMENT);
        desc.setPreferredSize(new Dimension(THUMB_W, 40));
        desc.setMaximumSize(new Dimension(THUMB_W, 40));
        name.setAlignmentX(LEFT_ALIGNMENT);
        text.add(name);
        text.add(Box.createVerticalStrut(6));
        text.add(desc);
        card.add(text, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        foot.setOpaque(false);
        JButton play = new JButton("Play " + kind.label);
        play.setFocusable(false);
        play.addActionListener(e -> start(kind));
        foot.add(play);
        if (kind == MapKind.RANDOM) {
            rerollBtn.setFocusable(false);
            rerollBtn.setToolTipText("Generate another random world");
            rerollBtn.addActionListener(e -> {
                randomSeed = System.nanoTime();
                loadPreviews(MapKind.RANDOM);
            });
            foot.add(rerollBtn);
        }
        card.add(foot, BorderLayout.SOUTH);

        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!busy) card.setBackground(PANEL_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(PANEL);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                start(kind);
            }
        };
        card.addMouseListener(hover);
        thumb.addMouseListener(hover);
        text.addMouseListener(hover);
        cards.put(kind, card);
        return card;
    }

    private void start(MapKind kind) {
        if (busy) return;
        listener.play(kind, kind == MapKind.RANDOM ? randomSeed : System.nanoTime());
    }

    /** Called by the window while a world is being generated. */
    public void setBusy(String message) {
        busy = message != null;
        statusLabel.setText(busy ? message : " ");
        setEnabledDeep(!busy);
    }

    public void setCanResume(boolean can) {
        resumeBtn.setVisible(can);
    }

    private void setEnabledDeep(boolean enabled) {
        for (JPanel card : cards.values()) setEnabledRecursive(card, enabled);
        resumeBtn.setEnabled(enabled);
        loadBtn.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.DEFAULT_CURSOR : Cursor.WAIT_CURSOR));
    }

    private static void setEnabledRecursive(java.awt.Component c, boolean enabled) {
        if (c instanceof JButton) c.setEnabled(enabled);
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) setEnabledRecursive(child, enabled);
        }
    }

    // ---------------------------------------------------------------- previews

    private void loadPreviews(MapKind... kinds) {
        int generation = ++previewGeneration;
        for (MapKind kind : kinds) {
            JLabel thumb = thumbs.get(kind);
            thumb.setIcon(null);
            thumb.setText("Preview...");
        }
        long seed = randomSeed;
        Thread t = new Thread(() -> {
            for (MapKind kind : kinds) {
                try {
                    World w = kind == MapKind.RANDOM ? MapGenerator.generate(seed) : RealMapGenerator.generate(kind, 1);
                    BufferedImage full = MapRenderer.newImage();
                    MapRenderer.render(w, full, true);
                    BufferedImage small = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = small.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(full.getScaledInstance(THUMB_W, THUMB_H, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
                    g.dispose();
                    SwingUtilities.invokeLater(() -> {
                        if (generation != previewGeneration && kind == MapKind.RANDOM) return;
                        thumbs.get(kind).setText(null);
                        thumbs.get(kind).setIcon(new ImageIcon(small));
                    });
                } catch (RuntimeException ex) {
                    SwingUtilities.invokeLater(() -> thumbs.get(kind).setText("(no preview)"));
                }
            }
        }, "menu-previews");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setPaint(new GradientPaint(0, 0, new Color(0x0F1B2A), 0, getHeight(), new Color(0x1F3B57)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }
}
