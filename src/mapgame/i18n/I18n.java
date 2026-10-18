package mapgame.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * Text lookup for the user interface and the game log. Texts live in {@code /mapgame/lang/<language>.properties}
 * (UTF-8); a missing key falls back to English. The Serbo-Croatian Cyrillic texts are generated from the Latin file.
 */
public final class I18n {
    private static final String PREF_KEY = "language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(I18n.class);
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private static Properties english;
    private static Properties current;
    private static Language language;
    /** English country name to its name in the current language. */
    private static Map<String, String> countryNames = Map.of();

    private I18n() {
    }

    static {
        Language saved = Language.fromCode(PREFS.get(PREF_KEY, ""));
        load(saved != null ? saved : Language.fromSystem());
    }

    public static Language language() {
        return language;
    }

    public static Locale locale() {
        return language.locale;
    }

    /** Switches the language, remembers it for the next start and notifies the listeners. */
    public static void setLanguage(Language l) {
        if (l == language) return;
        load(l);
        PREFS.put(PREF_KEY, l.code);
        for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    /**
     * Looks up a text. Without arguments the text is returned as written; with arguments it is a
     * {@link String#format} pattern (use positional arguments such as {@code %1$s}) using the language's number format.
     */
    public static String tr(String key, Object... args) {
        String text = current.getProperty(key);
        if (text == null) text = english.getProperty(key, key);
        return args.length == 0 ? text : String.format(language.locale, text, args);
    }

    /** Name of a real country in the current language; invented nations and unknown names stay as they are. */
    public static String countryName(String name) {
        return countryNames.getOrDefault(name, name);
    }

    private static void load(Language l) {
        if (english == null) english = read(Language.EN);
        language = l;
        current = l == Language.EN ? english : read(l);
        countryNames = readCountries(l);
    }

    /** {@code countries.tsv}: English name, German, Serbo-Croatian (Latin), Russian. */
    private static Map<String, String> readCountries(Language l) {
        int column = switch (l) {
            case DE -> 1;
            case SH_LATN, SH_CYRL -> 2;
            case RU -> 3;
            default -> -1;
        };
        if (column < 0) return Map.of();
        String path = "/mapgame/lang/countries.tsv";
        Map<String, String> names = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (f.length <= column || f[column].isBlank()) continue;
                names.put(f[0], l.transliterate ? Cyrillizer.convert(f[column]) : f[column]);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
        return names;
    }

    private static Properties read(Language l) {
        Properties p = new Properties();
        String path = "/mapgame/lang/" + l.file + ".properties";
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
        for (String key : p.stringPropertyNames()) {
            String value = p.getProperty(key);
            if (l.transliterate) value = Cyrillizer.convert(value);
            p.setProperty(key, value.replace("{", "").replace("}", ""));
        }
        return p;
    }
}
