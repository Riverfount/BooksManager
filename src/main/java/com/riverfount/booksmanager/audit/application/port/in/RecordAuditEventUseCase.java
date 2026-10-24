package com.riverfount.booksmanager.audit.application.port.in;

/**
 * Records an operation in the audit trail (RF27). It runs in the transaction of the operation it records, so an
 * operation that is rolled back leaves no entry, and the entry is lost with it. It must be called after the
 * operation has succeeded in the domain, and never throws for a missing actor.
 */
public interface RecordAuditEventUseCase {

    /** Records the event as done by the authenticated caller (nobody, if there is none, as at startup). */
    void record(AuditEvent event);

    /**
     * Records the event as done by the given user, for what happens before there is an authenticated caller (the
     * login). A {@code null} actor records it as done by nobody, for a login that names no known user.
     */
    void recordAs(Long actorId, AuditEvent event);
}
