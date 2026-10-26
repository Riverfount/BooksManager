package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.user.application.port.in.BlockReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.DeactivateReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetReaderUseCase;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.ListReadersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersUseCase;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.RevokeReaderAccessUseCase;
import com.riverfount.booksmanager.user.application.port.in.UnblockReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderUseCase;
import com.riverfount.booksmanager.user.application.port.out.ReaderFilter;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the reader use cases: fetches what the domain needs to
 * decide (does the document already exist?), calls the domain and
 * persists. The business decisions live in the domain (Reader.create,
 * Reader.validateUniqueDocument). Nothing here logs personal data.
 */
@Service
@Transactional
class ReaderService implements RegisterReaderUseCase, GetReaderUseCase, ListReadersUseCase,
        UpdateReaderUseCase, DeactivateReaderUseCase, BlockReaderUseCase, UnblockReaderUseCase {

    private final ReaderRepository readerRepository;
    private final Clock clock;
    private final RecordAuditEventUseCase audit;
    private final RevokeReaderAccessUseCase readerAccess;

    ReaderService(ReaderRepository readerRepository, Clock clock, RecordAuditEventUseCase audit,
            RevokeReaderAccessUseCase readerAccess) {
        this.readerRepository = readerRepository;
        this.clock = clock;
        this.audit = audit;
        this.readerAccess = readerAccess;
    }

    @Override
    public Reader register(RegisterReaderCommand command) {
        var reader = Reader.create(command.name(), command.document(), command.email(), command.phone(),
                command.birthDate(), guardianOf(command), LocalDate.now(clock));
        Reader.validateUniqueDocument(reader.getDocument(), readerRepository.existsByDocument(reader.getDocument()));
        return readerRepository.save(reader);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reader> findById(Long id) {
        return readerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Reader> list(ListReadersQuery query) {
        var filter = new ReaderFilter(query.name(), query.document(), query.active(), query.blocked());
        return readerRepository.findAll(filter, query.page());
    }

    @Override
    public Reader update(UpdateReaderCommand command) {
        var reader = loadExisting(command.id());
        // a null field keeps the current value; the rules (a minor needs the
        // guardian, valid formats) are checked by the domain on the resulting state
        reader.updateDetails(
                command.name() != null ? command.name() : reader.getName(),
                command.document() != null ? command.document() : reader.getDocument(),
                command.email() != null ? command.email() : reader.getEmail(),
                command.phone() != null ? command.phone() : reader.getPhone(),
                command.birthDate() != null ? command.birthDate() : reader.getBirthDate(),
                mergedGuardian(reader.getGuardian(), command),
                LocalDate.now(clock));
        // the check ignores the reader itself: keeping its own document is not a repetition
        Reader.validateUniqueDocument(reader.getDocument(),
                readerRepository.existsByDocumentAndIdNot(reader.getDocument(), reader.getId()));
        return readerRepository.save(reader);
    }

    @Override
    public void deactivate(Long id) {
        var reader = loadExisting(id);
        var wasActive = reader.isActive();
        reader.deactivate();
        readerRepository.save(reader);
        if (wasActive) {
            audit.record(new AuditEvent(AuditAction.READER_DEACTIVATED, AuditTarget.READER, id, null));
        }
        // an inactive reader cannot keep the access (Phase 6); idempotent, so it also cleans up what was left behind
        readerAccess.revoke(id);
    }

    @Override
    public void block(Long id) {
        var reader = loadExisting(id);
        var wasBlocked = reader.isBlocked();
        reader.block();
        readerRepository.save(reader);
        if (!wasBlocked) {
            audit.record(new AuditEvent(AuditAction.READER_BLOCKED, AuditTarget.READER, id, null));
        }
    }

    @Override
    public void unblock(Long id) {
        var reader = loadExisting(id);
        var wasBlocked = reader.isBlocked();
        reader.unblock();
        readerRepository.save(reader);
        if (wasBlocked) {
            audit.record(new AuditEvent(AuditAction.READER_UNBLOCKED, AuditTarget.READER, id, null));
        }
    }

    private Reader loadExisting(Long id) {
        return readerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("leitor não encontrado: " + id));
    }

    /**
     * The guardian fields sent replace the current ones, one by one; none sent
     * keeps the current guardian (or the lack of one).
     */
    private static Guardian mergedGuardian(Guardian current, UpdateReaderCommand command) {
        if (command.guardianName() == null && command.guardianEmail() == null
                && command.guardianPhone() == null) {
            return current;
        }
        return new Guardian(
                command.guardianName() != null || current == null ? command.guardianName() : current.name(),
                command.guardianEmail() != null || current == null ? command.guardianEmail() : current.email(),
                command.guardianPhone() != null || current == null ? command.guardianPhone() : current.phone());
    }

    /** No guardian fields at all means no guardian; any of them present builds (and validates) one. */
    private static Guardian guardianOf(RegisterReaderCommand command) {
        if (command.guardianName() == null && command.guardianEmail() == null
                && command.guardianPhone() == null) {
            return null;
        }
        return new Guardian(command.guardianName(), command.guardianEmail(), command.guardianPhone());
    }
}
