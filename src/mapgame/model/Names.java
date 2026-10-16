package mapgame.model;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;

final class Names {
    private static final String[] START = {"Ar", "Bel", "Cor", "Dal", "Eri", "Fen", "Gor", "Hal", "Ith", "Kar", "Lor",
            "Mar", "Nor", "Os", "Pel", "Quin", "Ra", "Sel", "Tor", "Ul", "Val", "Wes", "Yar", "Zan"};
    private static final String[] MID = {"a", "an", "en", "i", "o", "un", "ar", "el"};
    private static final String[] COUNTRY_END = {"ia", "land", "mark", "stan", "oria", "gard", "ica", "ovia", "shire", "ora"};
    private static final String[] PROVINCE_END = {"ton", "ford", "vale", "wick", "by", "haven", "field", "burg", "mouth", "dale"};

    private final Random rnd;
    private final Set<String> used = new HashSet<>();

    Names(Random rnd) {
        this.rnd = rnd;
    }

    String country() {
        return unique(START, COUNTRY_END, 1);
    }

    String province() {
        return unique(START, PROVINCE_END, 2);
    }

    private String unique(String[] starts, String[] ends, int midChanceDenominator) {
        for (int i = 0; i < 30; i++) {
            String mid = rnd.nextInt(3) < midChanceDenominator ? MID[rnd.nextInt(MID.length)] : "";
            String n = starts[rnd.nextInt(starts.length)] + mid + ends[rnd.nextInt(ends.length)];
            if (used.add(n)) return n;
        }
        String n = starts[rnd.nextInt(starts.length)] + ends[rnd.nextInt(ends.length)] + " " + (used.size() % 9 + 2);
        used.add(n);
        return n;
    }
}
