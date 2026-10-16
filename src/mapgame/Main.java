package mapgame;

import javax.swing.SwingUtilities;
import mapgame.ui.GameWindow;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow().begin());
    }
}
