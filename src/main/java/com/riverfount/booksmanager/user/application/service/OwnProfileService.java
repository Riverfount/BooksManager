package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetOwnProfileUseCase;
import com.riverfount.booksmanager.user.application.port.in.OwnProfile;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileUseCase;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reader maintains their own registration (Phase 6, RF32, RN22): contact data and password. The reader always
 * comes from the token, so there is no other reader to reach. The rules about the data live in {@link Reader}; here
 * the current password is checked where the rule asks for it. A wrong password counts toward the login lockout
 * (RNF13), and that record has to survive the exception that refuses it, so the refusal does not roll back (nothing
 * else has been written by then). The audit trail gets the fields that changed, never their values, nor a password.
 */
@Service
@Transactional
class OwnProfileService implements GetOwnProfileUseCase, UpdateOwnProfileUseCase,
        ChangeOwnPasswordUseCase {

    private final ReaderRepository readerRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RevokeRefreshTokensUseCase refreshTokens;
    private final RecordAuditEventUseCase audit;
    private final Clock clock;
    private final LoginLockoutProperties lockout;

    OwnProfileService(ReaderRepository readerRepository, UserRepository userRepository, PasswordHasher passwordHasher,
            RevokeRefreshTokensUseCase refreshTokens, RecordAuditEventUseCase audit, Clock clock,
            LoginLockoutProperties lockout) {
        this.readerRepository = readerRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
        this.lockout = lockout;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnProfile get(Long readerId) {
        return profileOf(readerRepository.findById(readerId)
                .orElseThrow(() -> new ResourceNotFoundException("leitor não encontrado: " + readerId)));
    }

    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public OwnProfile update(UpdateOwnProfileCommand command) {
        var reader = loadActiveReader(command.readerId());
        var previousPhone = reader.getPhone();
        var previousEmail = reader.getEmail();
        var previousGuardian = reader.getGuardian();
        // a null field keeps the current value; the domain checks the resulting state before anything is written
        reader.updateContact(
                command.phone() != null ? command.phone() : previousPhone,
                command.email() != null ? command.email() : previousEmail,
                mergedGuardian(previousGuardian, command),
                LocalDate.now(clock));

        var changed = new ArrayList<String>();
        if (!Objects.equals(previousPhone, reader.getPhone())) {
            changed.add("telefone");
        }
        if (!Objects.equals(previousEmail, reader.getEmail())) {
            changed.add("e-mail");
            // the e-mail is where the invitation and the password reset go (RN20), so changing it proves the password
            confirmCurrentPassword(command.readerId(), command.currentPassword());
        }
        if (!Objects.equals(previousGuardian, reader.getGuardian())) {
            changed.add("responsável");
        }
        var saved = readerRepository.save(reader);
        if (!changed.isEmpty()) {
            audit.record(new AuditEvent(AuditAction.READER_PROFILE_UPDATED, AuditTarget.READER, saved.getId(),
                    "campos: " + String.join(", ", changed)));
        }
        return profileOf(saved);
    }

    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void change(ChangeOwnPasswordCommand command) {
        loadActiveReader(command.readerId());
        PasswordPolicy.validate(command.newPassword());
        var user = confirmCurrentPassword(command.readerId(), command.currentPassword());
        user.changePasswordHash(passwordHasher.hash(command.newPassword()));
        userRepository.save(user);
        // every session ends: the reader signs in again with the new password
        refreshTokens.revokeAllOf(user.getId());
        audit.record(new AuditEvent(AuditAction.READER_PASSWORD_CHANGED, AuditTarget.USER, user.getId(), null));
    }

    /**
     * The locked access of the reader, after the current password is proved. A wrong one is counted like a wrong login
     * and refused; while the lockout is in force nothing is checked, so this route is no way around it.
     */
    private User confirmCurrentPassword(Long readerId, String currentPassword) {
        var user = userRepository.findActiveByReaderIdForUpdate(readerId)
                .orElseThrow(() -> new ResourceNotFoundException("acesso do leitor não encontrado"));
        var now = Instant.now(clock);
        if (!user.canAuthenticate(now)) {
            throw new BusinessRuleException(
                    "a conta está bloqueada temporariamente por tentativas de senha erradas; tente mais tarde");
        }
        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            var counted = user.registerFailedLogin(now, lockout.maxFailedAttempts(), lockout.blockDuration());
            if (counted) {
                userRepository.save(user);
            }
            audit.record(new AuditEvent(AuditAction.LOGIN_FAILURE, AuditTarget.USER, user.getId(),
                    "senha atual incorreta"));
            if (counted && user.isBlocked(now)) {
                audit.record(new AuditEvent(AuditAction.LOGIN_LOCKOUT, AuditTarget.USER, user.getId(),
                        "bloqueado até " + user.getBlockedUntil().truncatedTo(ChronoUnit.SECONDS) + " após "
                                + lockout.maxFailedAttempts() + " tentativas erradas"));
            }
            throw new BusinessRuleException("a senha atual está incorreta");
        }
        if (user.registerSuccessfulLogin()) {
            userRepository.save(user);
        }
        return user;
    }

    private OwnProfile profileOf(Reader reader) {
        return new OwnProfile(reader, reader.isMinor(LocalDate.now(clock)));
    }

    private Reader loadActiveReader(Long readerId) {
        var reader = readerRepository.findById(readerId)
                .orElseThrow(() -> new ResourceNotFoundException("leitor não encontrado: " + readerId));
        if (!reader.isActive()) {
            throw new BusinessRuleException("o leitor inativo não pode alterar o próprio cadastro");
        }
        return reader;
    }

    /** The guardian fields sent replace the current ones, one by one; none sent keeps the current guardian. */
    private static Guardian mergedGuardian(Guardian current, UpdateOwnProfileCommand command) {
        if (command.guardianName() == null && command.guardianEmail() == null && command.guardianPhone() == null) {
            return current;
        }
        return new Guardian(
                command.guardianName() != null || current == null ? command.guardianName() : current.name(),
                command.guardianEmail() != null || current == null ? command.guardianEmail() : current.email(),
                command.guardianPhone() != null || current == null ? command.guardianPhone() : current.phone());
    }
}
