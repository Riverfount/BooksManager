package com.riverfount.booksmanager.audit.application.port.out;

import java.util.Optional;

/**
 * The login of a system user, kept next to the id in the audit trail. Defined here, in the audit's language, and
 * implemented by the user module, so the audit never depends on it.
 */
public interface ActorLogins {

    Optional<String> loginOf(Long userId);
}
