package com.riverfount.booksmanager.user.adapter.in.web;

/**
 * Masks the reader's document in listings, so full documents are not
 * exposed in bulk (RNF11): everything becomes {@code *} except the last two
 * characters. A document of up to four characters is masked entirely, since
 * showing two of them would reveal too much.
 */
final class DocumentMask {

    private static final int VISIBLE_CHARACTERS = 2;
    private static final int MIN_LENGTH_TO_SHOW_ANY = 5;

    private DocumentMask() {
    }

    static String mask(String document) {
        if (document == null || document.isEmpty()) {
            return "";
        }
        var visible = document.length() >= MIN_LENGTH_TO_SHOW_ANY ? VISIBLE_CHARACTERS : 0;
        var hidden = document.length() - visible;
        return "*".repeat(hidden) + document.substring(hidden);
    }
}
