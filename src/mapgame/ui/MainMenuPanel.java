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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import mapgame.i18n.I18n;
import mapgame.i18n.Language;
import mapgame.model.MapGenerator;
import mapgame.model.MapKind;
import mapgame.model.RealMapGenerator;
import mapgame.model.World;
import mapgame.sim.Difficulty;

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
    private final Map<MapKind, JLabel> names = new EnumMap<>(MapKind.class);
    private final Map<MapKind, JTextArea> descriptions = new EnumMap<>(MapKind.class);
    private final Map<MapKind, JButton> playButtons = new EnumMap<>(MapKind.class);
    private final Set<MapKind> previewFailed = EnumSet.noneOf(MapKind.class);
    private final JButton resumeBtn = new JButton(), quitBtn = new JButton(), loadBtn = new JButton(), rerollBtn = new JButton();
    private final JLabel subtitle = new JLabel("", SwingConstants.CENTER), languageLabel = new JLabel();
    private final JComboBox<Language> languageBox = new JComboBox<>(Language.values());
    private final JComboBox<Difficulty> difficultyBox = new JComboBox<>(Difficulty.values());
    private final JSlider aggressionSlider = new JSlider(0, 100, 50);
    private final JLabel difficultyLabel = new JLabel(), aggressionLabel = new JLabel(), aggressionValue = new JLabel();
    private boolean applyingLanguage;
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

        JLabel title = new JLabel("AUTISTIC MAP GAME", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 64f));
        title.setForeground(TEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);
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

        difficultyBox.setFocusable(false);
        difficultyBox.setSelectedItem(Difficulty.NORMAL);
        difficultyBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, ((Difficulty) v).label(), i, s, f);
            }
        });
        aggressionSlider.setOpaque(false);
        aggressionSlider.setFocusable(false);
        aggressionSlider.setPreferredSize(new Dimension(160, 24));
        aggressionSlider.addChangeListener(e -> aggressionValue.setText(Integer.toString(aggressionSlider.getValue())));
        for (JLabel l : new JLabel[] {difficultyLabel, aggressionLabel, aggressionValue}) l.setForeground(MUTED);
        aggressionValue.setText("50");
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        settings.setOpaque(false);
        settings.add(difficultyLabel);
        settings.add(difficultyBox);
        settings.add(Box.createHorizontalStrut(18));
        settings.add(aggressionLabel);
        settings.add(aggressionSlider);
        settings.add(aggressionValue);
        settings.setAlignmentX(CENTER_ALIGNMENT);
        content.add(settings);
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
        content.add(Box.createVerticalStrut(16));

        languageBox.setFocusable(false);
        languageBox.addActionListener(e -> {
            if (!applyingLanguage) I18n.setLanguage((Language) languageBox.getSelectedItem());
        });
        languageLabel.setForeground(MUTED);
        JPanel languageRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        languageRow.setOpaque(false);
        languageRow.add(languageLabel);
        languageRow.add(languageBox);
        languageRow.setAlignmentX(CENTER_ALIGNMENT);
        content.add(languageRow);

        add(content, new GridBagConstraints());
        I18n.addListener(this::applyLanguage);
        applyLanguage();
        loadPreviews(MapKind.values());
    }

    /** Sets every text of the menu in the current language. */
    private void applyLanguage() {
        applyingLanguage = true;
        languageBox.setSelectedItem(I18n.language());
        applyingLanguage = false;
        subtitle.setText(I18n.tr("menu.subtitle"));
        languageLabel.setText(I18n.tr("menu.language"));
        difficultyLabel.setText(I18n.tr("menu.difficulty"));
        aggressionLabel.setText(I18n.tr("menu.aggression"));
        aggressionSlider.setToolTipText(I18n.tr("menu.aggression.tip"));
        difficultyBox.repaint();
        resumeBtn.setText(I18n.tr("menu.resume"));
        loadBtn.setText(I18n.tr("menu.load"));
        quitBtn.setText(I18n.tr("menu.quit"));
        rerollBtn.setText(I18n.tr("menu.reroll"));
        rerollBtn.setToolTipText(I18n.tr("menu.reroll.tip"));
        for (MapKind kind : MapKind.values()) {
            names.get(kind).setText(kind.label());
            descriptions.get(kind).setText(kind.description());
            playButtons.get(kind).setText(I18n.tr("menu.play", kind.label()));
            JLabel thumb = thumbs.get(kind);
            if (thumb.getIcon() == null) thumb.setText(I18n.tr(previewFailed.contains(kind) ? "menu.nopreview" : "menu.preview"));
        }
        revalidate();
        repaint();
    }

    private JPanel buildCard(MapKind kind) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3A5573), 2), BorderFactory.createEmptyBorder(12, 12, 14, 12)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel thumb = new JLabel("", SwingConstants.CENTER);
        thumb.setPreferredSize(new Dimension(THUMB_W, THUMB_H));
        thumb.setOpaque(true);
        thumb.setBackground(new Color(0x1B3A57));
        thumb.setForeground(MUTED);
        thumbs.put(kind, thumb);
        card.add(thumb, BorderLayout.NORTH);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel();
        names.put(kind, name);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 26f));
        name.setForeground(TEXT);
        JTextArea desc = new JTextArea();
        descriptions.put(kind, desc);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setEditable(false);
        desc.setFocusable(false);
        desc.setOpaque(false);
        desc.setBorder(null);
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 14f));
        desc.setForeground(MUTED);
        desc.setAlignmentX(LEFT_ALIGNMENT);
        desc.setPreferredSize(new Dimension(THUMB_W, 56));
        desc.setMaximumSize(new Dimension(THUMB_W, 56));
        name.setAlignmentX(LEFT_ALIGNMENT);
        text.add(name);
        text.add(Box.createVerticalStrut(6));
        text.add(desc);
        card.add(text, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        foot.setOpaque(false);
        JButton play = new JButton();
        playButtons.put(kind, play);
        play.setFocusable(false);
        play.addActionListener(e -> start(kind));
        foot.add(play);
        if (kind == MapKind.RANDOM) {
            rerollBtn.setFocusable(false);
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

    public Difficulty difficulty() {
        return (Difficulty) difficultyBox.getSelectedItem();
    }

    /** How eager the AI is to start wars, 0 - 100 (50 = normal). */
    public int aggression() {
        return aggressionSlider.getValue();
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
            previewFailed.remove(kind);
            thumb.setText(I18n.tr("menu.preview"));
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
                    SwingUtilities.invokeLater(() -> {
                        previewFailed.add(kind);
                        thumbs.get(kind).setText(I18n.tr("menu.nopreview"));
                    });
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
