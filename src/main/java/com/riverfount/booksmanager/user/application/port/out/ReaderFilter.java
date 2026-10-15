package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.user.domain.Reader;

/**
 * Optional filters of the reader listing (RF07); a null field means "no
 * filter on that field". The name matches by "contains", ignoring case; the
 * document matches by equality, after the same normalization as the
 * registration. Blank texts count as absent. The state filters are exact.
 */
public record ReaderFilter(String name, String document, Boolean active, Boolean blocked) {

    public ReaderFilter {
        name = name == null || name.isBlank() ? null : name.strip();
        var normalizedDocument = Reader.normalizeDocument(document);
        document = normalizedDocument.isEmpty() ? null : normalizedDocument;
    }

    public static ReaderFilter none() {
        return new ReaderFilter(null, null, null, null);
    }
}
