package mapgame.audio;

import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/**
 * Plays the generated sounds. Three modes: off, effects only, effects and music. The choice is remembered. If there is no
 * sound device the manager silently does nothing.
 */
public final class SoundManager {
    public enum Mode {
        OFF, EFFECTS, MUSIC;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final AudioFormat FORMAT = new AudioFormat(Synth.RATE, 16, 1, true, false);
    private static final int MAX_CLIPS = 6;
    private static final Preferences PREFS = Preferences.userNodeForPackage(SoundManager.class);

    private final Map<Synth.Cue, byte[]> effects = new EnumMap<>(Synth.Cue.class);
    private Clip music;
    private Mode mode;
    private boolean broken;
    private int playing;

    public SoundManager() {
        Mode saved = Mode.EFFECTS;
        try {
            saved = Mode.valueOf(PREFS.get("sound.mode", Mode.EFFECTS.name()));
        } catch (IllegalArgumentException ignored) {
            // keep the default
        }
        mode = saved;
        for (Synth.Cue c : Synth.Cue.values()) effects.put(c, Synth.effect(c));
        applyMode();
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = m;
        PREFS.put("sound.mode", m.name());
        applyMode();
    }

    /** Plays an effect (if effects are on). */
    public void play(Synth.Cue cue) {
        if (mode == Mode.OFF || broken || playing >= MAX_CLIPS) return;
        try {
            byte[] data = effects.get(cue);
            Clip clip = AudioSystem.getClip();
            clip.open(FORMAT, data, 0, data.length);
            playing++;
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    playing = Math.max(0, playing - 1);
                }
            });
            clip.start();
        } catch (Exception | LinkageError ex) {
            broken = true;
        }
    }

    private void applyMode() {
        if (broken) return;
        try {
            if (mode == Mode.MUSIC) {
                if (music == null) {
                    byte[] data = Synth.music();
                    music = AudioSystem.getClip();
                    music.open(new AudioInputStream(new java.io.ByteArrayInputStream(data), FORMAT, data.length / 2));
                    if (music.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        ((FloatControl) music.getControl(FloatControl.Type.MASTER_GAIN)).setValue(-8f);
                    }
                }
                if (!music.isRunning()) music.loop(Clip.LOOP_CONTINUOUSLY);
            } else if (music != null) {
                music.stop();
            }
        } catch (Exception | LinkageError ex) {
            broken = true;
            music = null;
        }
    }
}
