package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An administrator or an operator changes their own password (RF09), the way the reader does (RF32). The user always
 * comes from the token. The user is loaded locked; an inactive user and a reader access (which changes the password in
 * the reader area) are refused; the new password is checked against the policy first, so a weak one does not count as a
 * wrong attempt; then the current password is proved by {@link CurrentPasswordConfirmation}, whose count of wrong
 * attempts has to survive the exception that refuses them (so the refusal does not roll back, and nothing else has been
 * written by then). On success the hash is replaced and every refresh token of the user is revoked, so the user signs in
 * again; the audit trail gets the fact and never a password or a hash.
 */
@Service
@Transactional
class StaffPasswordService implements ChangeStaffPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RevokeRefreshTokensUseCase refreshTokens;
    private final RecordAuditEventUseCase audit;
    private final CurrentPasswordConfirmation confirmation;

    StaffPasswordService(UserRepository userRepository, PasswordHasher passwordHasher,
            RevokeRefreshTokensUseCase refreshTokens, RecordAuditEventUseCase audit, Clock clock,
            LoginLockoutProperties lockout) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.confirmation = new CurrentPasswordConfirmation(userRepository, passwordHasher, audit, clock, lockout);
    }

    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void change(ChangeStaffPasswordCommand command) {
        var user = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("usuário não encontrado: " + command.userId()));
        if (user.getRole() == Role.READER) {
            throw new BusinessRuleException("o acesso do leitor troca a senha na área do leitor");
        }
        if (!user.isActive()) {
            throw new BusinessRuleException("o usuário inativo não pode trocar a senha");
        }
        PasswordPolicy.validate(command.newPassword());
        confirmation.confirm(user, command.currentPassword());
        user.changePasswordHash(passwordHasher.hash(command.newPassword()));
        userRepository.save(user);
        // every session ends: the user signs in again with the new password
        refreshTokens.revokeAllOf(user.getId());
        audit.record(new AuditEvent(AuditAction.USER_PASSWORD_CHANGED, AuditTarget.USER, user.getId(), null));
    }
}
