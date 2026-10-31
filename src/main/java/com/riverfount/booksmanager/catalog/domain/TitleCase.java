package com.riverfount.booksmanager.catalog.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restores the casing of names and titles that arrive entirely in uppercase (typical of book data sources, and of
 * hand-typed text). It only acts on text that is wholly uppercase: anything with lowercase letters has a person's
 * casing in it ("McDonald", "II", "J.R.R.") and is returned untouched. Text without letters is also untouched.
 *
 * <p>For all-caps text it produces a Portuguese title case: connectives ("de", "da", "e"...) stay lowercase, except
 * as the first word, after a colon or as the last word; roman numerals and initials keep their uppercase; a few known
 * spellings (acronyms, "McDonald") are restored; the words after a hyphen or an apostrophe are capitalized too.
 *
 * <p>It is a heuristic: nothing can tell "MCDONALD" from "Mcdonald" once the casing is lost, so the result is a
 * suggestion that a person can still correct. The function is pure and idempotent.
 */
public final class TitleCase {

    private static final Set<String> CONNECTIVES = Set.of(
            "a", "as", "o", "os", "um", "uns", "uma", "umas",
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "num", "numa",
            "por", "pelo", "pela", "pelos", "pelas", "para", "pra", "com", "sem", "sob", "sobre",
            "ao", "aos", "à", "às", "e", "ou", "até", "entre");

    /** Words that announce a roman numeral ("Capítulo IV"), besides a numeral that ends the text ("Rei Arthur II"). */
    private static final Set<String> NUMERAL_ANNOUNCERS = Set.of(
            "capítulo", "cap", "volume", "vol", "tomo", "parte", "livro", "ato", "fase", "edição", "século",
            "rei", "rainha", "papa");

    /** Valid roman numerals that are far more often words or names ("Mix", "Di Caprio"). */
    private static final Set<String> NOT_NUMERALS = Set.of("MIX", "DI", "LI", "MI", "CI");

    /** Spellings that casing rules cannot guess, by lowercase word. */
    private static final Map<String, String> KNOWN_SPELLINGS = Map.ofEntries(
            Map.entry("mcdonald", "McDonald"),
            Map.entry("macdonald", "MacDonald"),
            Map.entry("mcluhan", "McLuhan"),
            Map.entry("mccartney", "McCartney"),
            Map.entry("onu", "ONU"),
            Map.entry("eua", "EUA"),
            Map.entry("usp", "USP"),
            Map.entry("unicamp", "Unicamp"),
            Map.entry("ufrj", "UFRJ"),
            Map.entry("ibge", "IBGE"),
            Map.entry("fgv", "FGV"),
            Map.entry("pib", "PIB"),
            Map.entry("tv", "TV"),
            Map.entry("dna", "DNA"),
            Map.entry("dvd", "DVD"),
            Map.entry("pdf", "PDF"));

    private static final Pattern ROMAN_NUMERAL = Pattern.compile(
            "(?=[IVXLCDM]{2,})M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");
    private static final Pattern INITIALS = Pattern.compile("\\p{L}(\\.\\p{L})+");
    private static final Pattern SPLIT_AFTER_HYPHEN_OR_APOSTROPHE = Pattern.compile("(?<=[-'’])");

    private TitleCase() {
    }

    /** The text in title case when it is entirely uppercase; the same text otherwise (null stays null). */
    public static String normalize(String text) {
        if (text == null || !isAllCaps(text)) {
            return text;
        }
        var tokens = text.split(" ", -1);
        int first = indexOfFirstWord(tokens);
        int last = indexOfLastWord(tokens);
        var result = new StringBuilder(text.length());
        String previousWord = null;
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            var token = tokens[i];
            if (token.isEmpty()) {
                continue;
            }
            result.append(convert(token, i == first, i == last, previousWord));
            previousWord = token;
        }
        return result.toString();
    }

    private static boolean isAllCaps(String text) {
        return text.chars().anyMatch(Character::isLetter) && text.equals(text.toUpperCase(Locale.ROOT));
    }

    private static int indexOfFirstWord(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfLastWord(String[] tokens) {
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (!tokens[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static String convert(String token, boolean first, boolean last, String previousWord) {
        int start = 0;
        int end = token.length();
        while (start < end && !Character.isLetterOrDigit(token.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(token.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return token;
        }
        var core = token.substring(start, end);
        return token.substring(0, start) + convertCore(core, first, last, previousWord) + token.substring(end);
    }

    private static String convertCore(String core, boolean first, boolean last, String previousWord) {
        var lower = core.toLowerCase(Locale.ROOT);
        var known = KNOWN_SPELLINGS.get(lower);
        if (known != null) {
            return known;
        }
        if (core.chars().anyMatch(Character::isDigit) || INITIALS.matcher(core).matches()) {
            return core;
        }
        if (isRomanNumeral(core, first, last, previousWord)) {
            return core;
        }
        boolean afterColon = previousWord != null && previousWord.endsWith(":");
        if (CONNECTIVES.contains(lower) && !first && !last && !afterColon) {
            return lower;
        }
        return capitalizeParts(lower);
    }

    private static boolean isRomanNumeral(String core, boolean first, boolean last, String previousWord) {
        if (first || NOT_NUMERALS.contains(core) || !ROMAN_NUMERAL.matcher(core).matches()) {
            return false;
        }
        return last || (previousWord != null && NUMERAL_ANNOUNCERS.contains(announcerKey(previousWord)));
    }

    private static String announcerKey(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
    }

    /** Capitalizes the word and each part that follows a hyphen or an apostrophe ("d'ávila" -> "D'Ávila"). */
    private static String capitalizeParts(String lowerWord) {
        var result = new StringBuilder(lowerWord.length());
        for (var part : SPLIT_AFTER_HYPHEN_OR_APOSTROPHE.split(lowerWord)) {
            if (!part.isEmpty()) {
                int firstCodePoint = part.codePointAt(0);
                result.appendCodePoint(Character.toUpperCase(firstCodePoint));
                result.append(part.substring(Character.charCount(firstCodePoint)));
            }
        }
        return result.toString();
    }
}
