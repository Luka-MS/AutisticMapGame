package mapgame.i18n;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Serbo-Croatian Latin script to Cyrillic (the mapping is one-to-one, including the digraphs lj, nj, dž). */
final class Cyrillizer {
    /** Format specifiers, HTML tags and {braced} spans (key names such as {M}) stay in Latin script. */
    private static final Pattern KEEP = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]|<[^>]*>|\\{[^}]*}");

    private Cyrillizer() {
    }

    static String convert(String latin) {
        StringBuilder out = new StringBuilder(latin.length());
        Matcher m = KEEP.matcher(latin);
        int pos = 0;
        while (m.find()) {
            letters(latin.substring(pos, m.start()), out);
            out.append(m.group());
            pos = m.end();
        }
        letters(latin.substring(pos), out);
        return out.toString();
    }

    private static void letters(String s, StringBuilder out) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lower = Character.toLowerCase(c);
            char lowerNext = i + 1 < s.length() ? Character.toLowerCase(s.charAt(i + 1)) : 0;
            boolean upper = Character.isUpperCase(c);
            if (lower == 'l' && lowerNext == 'j') {
                out.append(upper ? 'Љ' : 'љ');
                i++;
            } else if (lower == 'n' && lowerNext == 'j') {
                out.append(upper ? 'Њ' : 'њ');
                i++;
            } else if (lower == 'd' && lowerNext == 'ž') {
                out.append(upper ? 'Џ' : 'џ');
                i++;
            } else {
                out.append(single(c));
            }
        }
    }

    private static char single(char c) {
        return switch (c) {
            case 'a' -> 'а'; case 'A' -> 'А';
            case 'b' -> 'б'; case 'B' -> 'Б';
            case 'c' -> 'ц'; case 'C' -> 'Ц';
            case 'č' -> 'ч'; case 'Č' -> 'Ч';
            case 'ć' -> 'ћ'; case 'Ć' -> 'Ћ';
            case 'd' -> 'д'; case 'D' -> 'Д';
            case 'đ' -> 'ђ'; case 'Đ' -> 'Ђ';
            case 'e' -> 'е'; case 'E' -> 'Е';
            case 'f' -> 'ф'; case 'F' -> 'Ф';
            case 'g' -> 'г'; case 'G' -> 'Г';
            case 'h' -> 'х'; case 'H' -> 'Х';
            case 'i' -> 'и'; case 'I' -> 'И';
            case 'j' -> 'ј'; case 'J' -> 'Ј';
            case 'k' -> 'к'; case 'K' -> 'К';
            case 'l' -> 'л'; case 'L' -> 'Л';
            case 'm' -> 'м'; case 'M' -> 'М';
            case 'n' -> 'н'; case 'N' -> 'Н';
            case 'o' -> 'о'; case 'O' -> 'О';
            case 'p' -> 'п'; case 'P' -> 'П';
            case 'r' -> 'р'; case 'R' -> 'Р';
            case 's' -> 'с'; case 'S' -> 'С';
            case 'š' -> 'ш'; case 'Š' -> 'Ш';
            case 't' -> 'т'; case 'T' -> 'Т';
            case 'u' -> 'у'; case 'U' -> 'У';
            case 'v' -> 'в'; case 'V' -> 'В';
            case 'z' -> 'з'; case 'Z' -> 'З';
            case 'ž' -> 'ж'; case 'Ž' -> 'Ж';
            default -> c;
        };
    }
}
