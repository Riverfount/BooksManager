package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import java.util.Locale;

/**
 * The LIKE patterns of the searches (RF05). A text typed by the user is wrapped in {@code %}, and the wildcards
 * that it may contain ({@code %}, {@code _}) are escaped with {@code !}, so they stay literal; the queries declare
 * {@code escape '!'}. The letters are lowercased here, and the accents are left to the database: the queries apply
 * {@code unaccent} to both sides, so the comparison is the same whatever the user typed.
 */
final class LikePatterns {

    private LikePatterns() {
    }

    /** The pattern of "contains {@code term}", lowercased; null when there is no term. */
    static String contains(String term) {
        return term == null ? null : containsKeepingCase(term.toLowerCase(Locale.ROOT));
    }

    /** The pattern of "contains {@code term}", as typed (the ISBN, where the case matters for the X). */
    static String containsKeepingCase(String term) {
        if (term == null) {
            return null;
        }
        var escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
