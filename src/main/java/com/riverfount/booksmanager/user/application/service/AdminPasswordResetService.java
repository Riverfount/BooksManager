package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.user.application.port.in.AdminPasswordResetResult;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovers the access of the administrator who forgot the password, from the environment at startup (RF08, RF09), for
 * when no other administrator can reset it in the Users screen. Only an active administrator is touched: an operator,
 * a reader, an unknown login or an inactive administrator (reactivating it is a separate, deliberate act by another administrator) is left as
 * it is. The password, the login block and the refresh tokens change in one transaction, so a failure leaves none of
 * them changed; the audit trail gets the fact with no actor (nobody is authenticated at startup) and never a login,
 * a password or a hash. If the stored password already is the new one, as in a restart with the variable still set,
 * nothing is written.
 */
@Service
@Transactional
class AdminPasswordResetService implements ResetAdminPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RevokeRefreshTokensUseCase refreshTokens;
    private final RecordAuditEventUseCase audit;

    AdminPasswordResetService(UserRepository userRepository, PasswordHasher passwordHasher,
            RevokeRefreshTokensUseCase refreshTokens, RecordAuditEventUseCase audit) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Override
    public AdminPasswordResetResult reset(ResetAdminPasswordCommand command) {
        if (isBlank(command.login()) || isBlank(command.password())) {
            return AdminPasswordResetResult.NOT_CONFIGURED;
        }
        PasswordPolicy.validate(command.password());
        var found = userRepository.findByLogin(command.login());
        if (found.isEmpty()) {
            return AdminPasswordResetResult.NOT_FOUND;
        }
        if (found.get().getRole() != Role.ADMIN) {
            return AdminPasswordResetResult.NOT_AN_ADMINISTRATOR;
        }
        // locked, so a concurrent change of the same user is handled one after the other
        var admin = userRepository.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (!admin.isActive()) {
            return AdminPasswordResetResult.INACTIVE;
        }
        if (passwordHasher.matches(command.password(), admin.getPasswordHash())) {
            return AdminPasswordResetResult.ALREADY_UP_TO_DATE;
        }
        admin.changePasswordHash(passwordHasher.hash(command.password()));
        admin.unblock();
        userRepository.save(admin);
        refreshTokens.revokeAllOf(admin.getId());
        audit.record(new AuditEvent(AuditAction.USER_PASSWORD_RESET, AuditTarget.USER, admin.getId(),
                "senha redefinida na subida, pelo ambiente"));
        return AdminPasswordResetResult.RESET;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
