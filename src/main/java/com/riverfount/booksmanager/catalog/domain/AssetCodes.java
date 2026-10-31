package com.riverfount.booksmanager.catalog.domain;

/**
 * The format the library suggests for an asset code (RN02): a prefix, a hyphen and a sequence number padded to at
 * least five digits ({@code PAT-00001}). The format is only a suggestion: a copy may be registered with any valid
 * free-text code, so codes typed before the suggestion existed, or labels already printed, keep working.
 *
 * <p>The prefix comes from the library configuration; this class does not decide it, it only formats and numbers.
 * The number grows past five digits when it has to ({@code PAT-100000}). Only the numbers up to {@link #MAX_DIGITS}
 * digits are looked at when counting the existing codes, so a long run of digits can never overflow the counter.
 */
public final class AssetCodes {

    /** The digits a suggested number has at least (zero-padded). */
    public static final int MIN_DIGITS = 5;
    /** The most digits of an existing number that counts toward the next one. */
    public static final int MAX_DIGITS = 9;

    private AssetCodes() {
    }

    /** The code of the given number: {@code format("PAT", 8)} is {@code PAT-00008}. */
    public static String format(String prefix, long number) {
        return prefix + "-" + String.format("%0" + MIN_DIGITS + "d", number);
    }

    /** The next code, given the highest number already used with the prefix (zero when there is none). */
    public static String next(String prefix, long highestNumber) {
        return format(prefix, highestNumber + 1);
    }
}
