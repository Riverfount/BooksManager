package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Suggests the asset code for the next copy (RN02), to spare the operator from typing it. It is only a suggestion:
 * it reserves nothing, and a copy may be registered with any valid code.
 */
public interface SuggestNextAssetCodeUseCase {

    /** The next code of the library's format: the highest number used with the current prefix plus one. */
    String suggest();
}
