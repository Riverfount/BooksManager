package com.riverfount.booksmanager.audit.application.port.out;

import java.util.Optional;

/** The user making the current request, as the audit trail needs to know it. */
public interface CurrentActor {

    /** The id of the authenticated system user, or empty when nobody is authenticated (as at startup). */
    Optional<Long> id();
}
