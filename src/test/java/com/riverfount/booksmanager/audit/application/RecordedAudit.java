package com.riverfount.booksmanager.audit.application;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory stand-in for the audit's inbound port, for the tests of the use cases that report to it (RF27): it
 * keeps what was reported, and who it was reported as, so a test can say what the trail must have received.
 */
public final class RecordedAudit implements RecordAuditEventUseCase {

    /** An event and who it was reported as: {@code asCaller} for the authenticated caller, else the given actor. */
    public record Recorded(boolean asCaller, Long actorId, AuditEvent event) {
    }

    private final List<Recorded> recorded = new ArrayList<>();

    @Override
    public void record(AuditEvent event) {
        recorded.add(new Recorded(true, null, event));
    }

    @Override
    public void recordAs(Long actorId, AuditEvent event) {
        recorded.add(new Recorded(false, actorId, event));
    }

    public List<Recorded> all() {
        return List.copyOf(recorded);
    }

    public List<AuditEvent> events() {
        return recorded.stream().map(Recorded::event).toList();
    }

    public List<AuditAction> actions() {
        return events().stream().map(AuditEvent::action).toList();
    }

    public AuditEvent only() {
        if (recorded.size() != 1) {
            throw new AssertionError("expected exactly one audit event but there were " + recorded.size() + ": "
                    + events());
        }
        return recorded.getFirst().event();
    }

    public boolean isEmpty() {
        return recorded.isEmpty();
    }
}
