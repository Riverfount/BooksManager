package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Proves the current password of a user before something sensitive changes (RNF13), for the reader and for the staff
 * alike, so the two cannot drift apart. A wrong password is counted like a wrong login and refused (and reported to the
 * audit trail without the value); while the lockout is in force nothing is checked, so these routes are no way around
 * it; a right password clears the count. The caller loads the user locked and runs inside a transaction that does not
 * roll back on {@link BusinessRuleException}, so the count survives the refusal.
 */
final class CurrentPasswordConfirmation {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RecordAuditEventUseCase audit;
    private final Clock clock;
    private final LoginLockoutProperties lockout;

    CurrentPasswordConfirmation(UserRepository userRepository, PasswordHasher passwordHasher,
            RecordAuditEventUseCase audit, Clock clock, LoginLockoutProperties lockout) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.clock = clock;
        this.lockout = lockout;
    }

    /** @throws BusinessRuleException when the account is locked out or the password is wrong */
    void confirm(User user, String currentPassword) {
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
    }
}
