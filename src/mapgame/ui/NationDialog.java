package mapgame.ui;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import mapgame.i18n.I18n;
import mapgame.model.Province;
import mapgame.sim.Game;

/** Non-modal window with the nation-management pages: buildings, research, policies, diplomacy and so on. */
public final class NationDialog extends JDialog {
    private final MapPanel map;
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<NationTab> pages = new ArrayList<>();
    private Game game;

    public NationDialog(JFrame owner, MapPanel map) {
        super(owner, "", false);
        this.map = map;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        add(tabs);
        addPage(new BuildingsTab());
        addPage(new ResearchTab());
        addPage(new PolicyTab());
        addPage(new DiplomacyTab());
        addPage(new StatisticsTab());
        setPreferredSize(new Dimension(720, 520));
        pack();
        I18n.addListener(this::applyLanguage);
        applyLanguage();
    }

    private void addPage(NationTab t) {
        pages.add(t);
        tabs.addTab("", t.component());
    }

    public void bind(Game g) {
        game = g;
        refresh();
    }

    /** Selects the page with the given title key (for example "tab.buildings"). */
    public void show(String titleKey) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).titleKey().equals(titleKey)) tabs.setSelectedIndex(i);
        setVisible(true);
        toFront();
        refresh();
    }

    public void refresh() {
        if (game == null || !isVisible()) return;
        Province sel = map.selectedProvince();
        for (NationTab t : pages) t.refresh(game, sel);
    }

    private void applyLanguage() {
        setTitle(I18n.tr("nation.title"));
        for (int i = 0; i < pages.size(); i++) {
            tabs.setTitleAt(i, I18n.tr(pages.get(i).titleKey()));
            pages.get(i).applyLanguage();
        }
        refresh();
    }
}
