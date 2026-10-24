package com.riverfount.booksmanager.audit.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogQuery;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogUseCase;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.application.port.out.ActorLogins;
import com.riverfount.booksmanager.audit.application.port.out.AuditEntryRepository;
import com.riverfount.booksmanager.audit.application.port.out.AuditFilter;
import com.riverfount.booksmanager.audit.application.port.out.CurrentActor;
import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and lists the audit trail (RF27). Recording joins the transaction of the operation it records, so an
 * operation that is rolled back takes its entry with it; the moment comes from the application clock and the actor
 * from the authenticated caller. It only orchestrates: the entry validates itself.
 */
@Service
@Transactional
class AuditService implements RecordAuditEventUseCase, ListAuditLogUseCase {

    private final AuditEntryRepository repository;
    private final CurrentActor currentActor;
    private final ActorLogins actorLogins;
    private final Clock clock;

    AuditService(AuditEntryRepository repository, CurrentActor currentActor, ActorLogins actorLogins, Clock clock) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.actorLogins = actorLogins;
        this.clock = clock;
    }

    @Override
    public void record(AuditEvent event) {
        store(currentActor.id().orElse(null), event);
    }

    @Override
    public void recordAs(Long actorId, AuditEvent event) {
        store(actorId, event);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditEntry> list(ListAuditLogQuery query) {
        var zone = clock.getZone();
        var from = query.from() == null ? null : query.from().atStartOfDay(zone).toInstant();
        var toExclusive = query.to() == null ? null : query.to().plusDays(1).atStartOfDay(zone).toInstant();
        return repository.findAll(
                new AuditFilter(query.actorId(), query.action(), query.target(), query.targetId(), from, toExclusive),
                query.page());
    }

    private void store(Long actorId, AuditEvent event) {
        var login = actorId == null ? null : actorLogins.loginOf(actorId).orElse(null);
        repository.save(AuditEntry.record(Instant.now(clock), actorId, login, event.action(), event.target(),
                event.targetId(), event.summary()));
    }
}
