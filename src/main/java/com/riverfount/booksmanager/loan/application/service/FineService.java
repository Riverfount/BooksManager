package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.loan.application.port.in.PayFineUseCase;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineCommand;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays and waives fines (RF21, HU10). It only orchestrates: loads the fine, lets the domain
 * decide ({@link Fine#pay}, {@link Fine#waive}: a fine is resolved once, a waiver needs a
 * justification) and persists. A fine that is no longer pending stops blocking new loans
 * (RN05) because the eligibility only looks at pending ones. Each one is reported to the audit trail (RF27),
 * after the domain has accepted it, in the same transaction.
 */
@Service
@Transactional
class FineService implements PayFineUseCase, WaiveFineUseCase {

    private final FineRepository fineRepository;
    private final RecordAuditEventUseCase audit;

    FineService(FineRepository fineRepository, RecordAuditEventUseCase audit) {
        this.fineRepository = fineRepository;
        this.audit = audit;
    }

    @Override
    public Fine pay(Long fineId) {
        var fine = load(fineId);
        fine.pay();
        var saved = fineRepository.save(fine);
        audit.record(new AuditEvent(AuditAction.FINE_PAID, AuditTarget.FINE, fineId, null));
        return saved;
    }

    @Override
    public Fine waive(WaiveFineCommand command) {
        var fine = load(command.fineId());
        fine.waive(command.reason());
        var saved = fineRepository.save(fine);
        audit.record(new AuditEvent(AuditAction.FINE_WAIVED, AuditTarget.FINE, command.fineId(),
                "abono: " + command.reason().strip()));
        return saved;
    }

    private Fine load(Long fineId) {
        return fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("multa não encontrada: " + fineId));
    }
}
