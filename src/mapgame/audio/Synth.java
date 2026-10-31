package mapgame.audio;

/**
 * Generates all sounds in code (no sound files): short effects and a slow ambient music loop. Everything is
 * 16-bit signed little-endian mono PCM at {@link #RATE} Hz.
 */
public final class Synth {
    public static final float RATE = 22050f;

    /** Sounds the game can play. */
    public enum Cue {
        /** A war was declared on or by the player. */
        WAR,
        /** Peace. */
        PEACE,
        /** Good news: a province taken, a building or research finished. */
        GOOD,
        /** Bad news: an army or fleet lost, a province lost. */
        BAD
    }

    private Synth() {
    }

    /** One note: frequency in Hz, start and length in seconds, loudness 0 - 1. */
    private record Note(double freq, double start, double length, double amp) {
    }

    public static byte[] effect(Cue cue) {
        return switch (cue) {
            case WAR -> render(1.6, new Note(196, 0, 0.7, 0.8), new Note(147, 0.45, 1.1, 0.8), new Note(98, 0.45, 1.1, 0.5));
            case PEACE -> render(1.6, new Note(261.6, 0, 0.6, 0.6), new Note(329.6, 0.22, 0.6, 0.6), new Note(392, 0.44, 0.9, 0.6),
                    new Note(523.3, 0.66, 0.9, 0.5));
            case GOOD -> render(0.8, new Note(659.3, 0, 0.35, 0.55), new Note(880, 0.14, 0.55, 0.55));
            case BAD -> render(0.9, new Note(220, 0, 0.3, 0.7), new Note(207.7, 0.28, 0.5, 0.7), new Note(130.8, 0.28, 0.5, 0.4));
        };
    }

    private static byte[] render(double seconds, Note... notes) {
        int n = (int) (seconds * RATE);
        double[] mix = new double[n];
        for (Note note : notes) {
            int from = (int) (note.start * RATE), len = (int) (note.length * RATE);
            for (int i = 0; i < len && from + i < n; i++) {
                double t = i / (double) RATE;
                double attack = Math.min(1, t / 0.008);
                double decay = Math.exp(-4.0 * t / note.length);
                // A little second harmonic makes the tone warmer than a plain sine.
                double v = Math.sin(2 * Math.PI * note.freq * t) + 0.35 * Math.sin(4 * Math.PI * note.freq * t);
                mix[from + i] += v * attack * decay * note.amp;
            }
        }
        return toPcm(mix, 0.55);
    }

    /** Seconds per chord and number of chords in the music loop. */
    public static final int CHORD_SECONDS = 8, CHORDS = 4;

    /** A slow ambient loop: soft pads on Am - F - C - G with a quiet pluck arpeggio on top. */
    public static byte[] music() {
        double[][] chords = {
            {220.0, 261.6, 329.6}, // A minor
            {174.6, 220.0, 261.6}, // F major
            {196.0, 261.6, 329.6}, // C major
            {196.0, 246.9, 293.7}, // G major
        };
        int n = (int) (CHORD_SECONDS * CHORDS * RATE);
        double[] mix = new double[n];
        for (int c = 0; c < CHORDS; c++) {
            int from = (int) (c * CHORD_SECONDS * RATE), len = (int) (CHORD_SECONDS * RATE);
            for (int i = 0; i < len; i++) {
                double t = i / (double) RATE;
                // Smooth fade in and out so the chords overlap without clicks.
                double env = Math.min(1, Math.min(t / 2.0, (CHORD_SECONDS - t) / 2.0));
                double pad = 0;
                for (double f : chords[c]) pad += Math.sin(2 * Math.PI * f * t) + 0.5 * Math.sin(2 * Math.PI * f * 0.5 * t);
                double tremolo = 0.85 + 0.15 * Math.sin(2 * Math.PI * 0.25 * t);
                mix[from + i] += pad * env * tremolo * 0.16;
            }
            // Pluck arpeggio, one note every 0.75 s.
            for (int k = 0; k < CHORD_SECONDS / 0.75; k++) {
                double f = chords[c][k % 3] * 2;
                int start = from + (int) (k * 0.75 * RATE);
                for (int i = 0; i < (int) (1.2 * RATE) && start + i < n; i++) {
                    double t = i / (double) RATE;
                    mix[start + i] += Math.sin(2 * Math.PI * f * t) * Math.exp(-5.0 * t) * Math.min(1, t / 0.01) * 0.12;
                }
            }
        }
        return toPcm(mix, 0.6);
    }

    /** Scales to the given peak level (0 - 1) and converts to 16-bit PCM. */
    private static byte[] toPcm(double[] mix, double peakLevel) {
        double peak = 1e-9;
        for (double v : mix) peak = Math.max(peak, Math.abs(v));
        double scale = peakLevel * 32767 / peak;
        byte[] out = new byte[mix.length * 2];
        for (int i = 0; i < mix.length; i++) {
            short s = (short) Math.max(-32768, Math.min(32767, Math.round(mix[i] * scale)));
            out[2 * i] = (byte) s;
            out[2 * i + 1] = (byte) (s >> 8);
        }
        return out;
    }
}
