package com.riverfount.booksmanager.catalog.application.port.out;

/**
 * Outbound port of the catalog, in its own language: the prefix of the asset codes the library suggests
 * ({@code PAT} in {@code PAT-00001}). The prefix is a rule of the library, set in its configuration (RF26, RN16),
 * which belongs to the loan module; the catalog does not know it. As with {@link ActiveLoanChecker}, the loan module
 * provides the implementation and the catalog depends only on this interface, so there is no cycle between the
 * modules.
 */
public interface AssetCodePrefix {

    /** The prefix in force now: 3 to 4 uppercase letters. It is read at each call, so a change applies at once. */
    String prefix();
}
