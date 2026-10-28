package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.user.application.port.in.SeedAccountsUseCase;
import com.riverfount.booksmanager.user.application.port.in.SeedOperatorCommand;
import com.riverfount.booksmanager.user.application.port.in.SeedReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.SeedResult;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the accounts created at startup from the environment. The reader and its user are created in one
 * transaction, so there is never a reader left without the access that was asked for. Nobody is authenticated at
 * startup, so the audit entries have no actor; they never carry a password or a hash.
 */
@Service
@Transactional
class SeedAccountsService implements SeedAccountsUseCase {

    private final UserRepository userRepository;
    private final ReaderRepository readerRepository;
    private final PasswordHasher passwordHasher;
    private final RecordAuditEventUseCase audit;
    private final Clock clock;

    SeedAccountsService(UserRepository userRepository, ReaderRepository readerRepository,
            PasswordHasher passwordHasher, RecordAuditEventUseCase audit, Clock clock) {
        this.userRepository = userRepository;
        this.readerRepository = readerRepository;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public SeedResult seedOperator(SeedOperatorCommand command) {
        if (isBlank(command.login()) || isBlank(command.password())) {
            return SeedResult.NOT_CONFIGURED;
        }
        if (loginTaken(command.login())) {
            return SeedResult.ALREADY_EXISTS;
        }
        PasswordPolicy.validate(command.password());
        var operator = userRepository.save(
                User.create(command.login(), passwordHasher.hash(command.password()), Role.OPERATOR));
        audit.record(new AuditEvent(AuditAction.USER_CREATED, AuditTarget.USER, operator.getId(),
                "operador criado na subida"));
        return SeedResult.CREATED;
    }

    @Override
    public SeedResult seedReader(SeedReaderCommand command) {
        if (isBlank(command.login()) || isBlank(command.password()) || isBlank(command.name())
                || isBlank(command.document()) || isBlank(command.email()) || command.birthDate() == null) {
            return SeedResult.NOT_CONFIGURED;
        }
        if (loginTaken(command.login())) {
            return SeedResult.ALREADY_EXISTS;
        }
        PasswordPolicy.validate(command.password());
        var reader = Reader.create(command.name(), command.document(), command.email(), null, command.birthDate(),
                null, LocalDate.now(clock));
        Reader.validateUniqueDocument(reader.getDocument(), readerRepository.existsByDocument(reader.getDocument()));
        var savedReader = readerRepository.save(reader);
        var access = userRepository.save(
                User.createReaderAccess(command.login(), passwordHasher.hash(command.password()), savedReader.getId()));
        audit.record(new AuditEvent(AuditAction.USER_CREATED, AuditTarget.USER, access.getId(),
                "leitor criado na subida, leitor: " + savedReader.getId()));
        return SeedResult.CREATED;
    }

    private boolean loginTaken(String login) {
        return userRepository.existsByLogin(User.normalizeLogin(login));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
