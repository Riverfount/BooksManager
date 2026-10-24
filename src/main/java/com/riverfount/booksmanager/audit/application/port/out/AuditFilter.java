package com.riverfount.booksmanager.audit.application.port.out;

import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import java.time.Instant;

/** The filters of the audit log, each optional ({@code null} means no filter); the period is {@code [from, toExclusive)}. */
public record AuditFilter(Long actorId, AuditAction action, AuditTarget target, Long targetId, Instant from,
        Instant toExclusive) {
}
