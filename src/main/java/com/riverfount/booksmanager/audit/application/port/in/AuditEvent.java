package com.riverfount.booksmanager.audit.application.port.in;

import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import java.util.Objects;

/**
 * What a use case reports to the audit trail (RF27): the action, what it acted on and a short summary. The
 * moment and the actor are not part of it: the audit module takes the moment from the application clock and the
 * actor from the authenticated caller. The summary never carries a password, a hash, a token or personal data
 * beyond what the operation needs.
 */
public record AuditEvent(AuditAction action, AuditTarget target, Long targetId, String summary) {

    public AuditEvent {
        Objects.requireNonNull(action, "action");
    }
}
