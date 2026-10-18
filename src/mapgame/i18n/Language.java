package mapgame.i18n;

import java.util.Locale;

/** A language the game can be played in. Serbo-Croatian is offered in both Latin and Cyrillic script. */
public enum Language {
    EN("English", "en", "en", false, Locale.US),
    DE("Deutsch", "de", "de", false, Locale.GERMANY),
    SH_LATN("Srpskohrvatski (latinica)", "sh_latn", "sh", false, Locale.forLanguageTag("sr-Latn-RS")),
    SH_CYRL("Српскохрватски (ћирилица)", "sh_cyrl", "sh", true, Locale.forLanguageTag("sr-Cyrl-RS")),
    RU("Русский", "ru", "ru", false, Locale.forLanguageTag("ru-RU"));

    public final String nativeName;
    public final String code;
    /** Name of the properties file in {@code /mapgame/lang/}. */
    final String file;
    /** The texts are written in Latin script and converted to Cyrillic when loaded. */
    final boolean transliterate;
    public final Locale locale;

    Language(String nativeName, String code, String file, boolean transliterate, Locale locale) {
        this.nativeName = nativeName;
        this.code = code;
        this.file = file;
        this.transliterate = transliterate;
        this.locale = locale;
    }

    @Override
    public String toString() {
        return nativeName;
    }

    public static Language fromCode(String code) {
        for (Language l : values()) if (l.code.equals(code)) return l;
        return null;
    }

    /** Best match for the language of the operating system; English when there is none. */
    public static Language fromSystem() {
        Locale sys = Locale.getDefault();
        return switch (sys.getLanguage()) {
            case "de" -> DE;
            case "ru" -> RU;
            case "sr" -> "Cyrl".equals(sys.getScript()) ? SH_CYRL : SH_LATN;
            case "hr", "bs", "sh" -> SH_LATN;
            default -> EN;
        };
    }
}
