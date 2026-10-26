package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.shared.application.port.out.RateLimiter;
import com.riverfount.booksmanager.shared.configuration.RateLimitProperties;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationCommand;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationUseCase;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessCommand;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderAccessInvitation;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetCommand;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetUseCase;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.in.RevokeReaderAccessUseCase;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenGenerator;
import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenRepository;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.configuration.ReaderAccessProperties;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken.Purpose;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the access of the readers to the system (Phase 6): invitation by e-mail, acceptance (which creates the
 * READER user), password reset and revocation. The token of a link only exists in the e-mail: here it is kept as its
 * hash, and neither the token, a password nor a hash ever reaches the audit trail or a log. The e-mail goes out after
 * the commit (see NotificationSender), so an unavailable mail server cannot undo the operation.
 *
 * <p>The acceptance and the reset are public routes: they report to the audit as the user they concern. Every
 * refusal of a link says the same thing, so the answer does not tell an unknown token from a used or expired one.
 * A refused login or password is an exception and rolls the whole operation back, so it leaves the link unused.
 */
@Service
@Transactional
class ReaderAccessService implements InviteReaderAccessUseCase, AcceptReaderInvitationUseCase,
        RequestReaderPasswordResetUseCase, ResetReaderPasswordUseCase, RevokeReaderAccessUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReaderAccessService.class);

    private static final String INVALID_INVITATION = "convite inválido ou expirado";
    private static final String INVALID_RESET_LINK = "link de redefinição de senha inválido ou expirado";

    private final ReaderRepository readerRepository;
    private final UserRepository userRepository;
    private final ReaderAccessTokenRepository accessTokens;
    private final ReaderAccessTokenGenerator tokenGenerator;
    private final PasswordHasher passwordHasher;
    private final NotificationSender notifications;
    private final RevokeRefreshTokensUseCase refreshTokens;
    private final RecordAuditEventUseCase audit;
    private final Clock clock;
    private final ReaderAccessProperties properties;
    private final LoginLockoutProperties lockout;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    ReaderAccessService(ReaderRepository readerRepository, UserRepository userRepository,
            ReaderAccessTokenRepository accessTokens, ReaderAccessTokenGenerator tokenGenerator,
            PasswordHasher passwordHasher, NotificationSender notifications, RevokeRefreshTokensUseCase refreshTokens,
            RecordAuditEventUseCase audit, Clock clock, RateLimiter rateLimiter, RateLimitProperties rateLimits,
            ReaderAccessProperties properties, LoginLockoutProperties lockout) {
        this.readerRepository = readerRepository;
        this.userRepository = userRepository;
        this.accessTokens = accessTokens;
        this.tokenGenerator = tokenGenerator;
        this.passwordHasher = passwordHasher;
        this.notifications = notifications;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
        this.properties = properties;
        this.lockout = lockout;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
    }

    @Override
    public ReaderAccessInvitation invite(InviteReaderAccessCommand command) {
        var reader = loadReader(command.readerId());
        if (!reader.isActive()) {
            throw new BusinessRuleException("leitor inativo não pode receber acesso");
        }
        if (userRepository.findActiveByReaderIdForUpdate(reader.getId()).isPresent()) {
            throw new BusinessRuleException("o leitor já tem acesso ao sistema");
        }
        var now = Instant.now(clock);
        var token = issue(Purpose.INVITATION, reader.getId(), properties.invitationValidity(), now);
        notifications.send(new Notification(reader.getEmail(), "Convite para acessar a biblioteca",
                "Olá, " + reader.getName() + "!\n\nVocê foi convidado a acessar a biblioteca. Para escolher seu login e sua "
                        + "senha, abra o link abaixo. Ele vale até " + token.expiresAt() + " e só pode ser usado uma vez:\n\n"
                        + properties.invitationLink(token.rawToken()) + "\n\nSe você não esperava este convite, ignore "
                        + "esta mensagem."));
        audit.record(new AuditEvent(AuditAction.READER_ACCESS_INVITED, AuditTarget.READER, reader.getId(), null));
        return new ReaderAccessInvitation(reader.getId(), token.expiresAt());
    }

    @Override
    public void accept(AcceptReaderInvitationCommand command) {
        var now = Instant.now(clock);
        var token = usableToken(command.token(), Purpose.INVITATION, now, INVALID_INVITATION);
        var reader = readerRepository.findById(token.getReaderId()).filter(Reader::isActive)
                .orElseThrow(() -> new BusinessRuleException(INVALID_INVITATION));
        if (userRepository.findActiveByReaderIdForUpdate(reader.getId()).isPresent()) {
            throw new BusinessRuleException(INVALID_INVITATION);
        }
        PasswordPolicy.validate(command.password());
        var user = User.createReaderAccess(command.login(), passwordHasher.hash(command.password()), reader.getId());
        User.validateUniqueLogin(user.getLogin(), userRepository.existsByLogin(user.getLogin()));
        var saved = userRepository.save(user);
        token.use(now);
        accessTokens.save(token);
        audit.recordAs(saved.getId(), new AuditEvent(AuditAction.READER_ACCESS_GRANTED, AuditTarget.USER,
                saved.getId(), "leitor: " + reader.getId()));
    }

    @Override
    public void requestReset(RequestReaderPasswordResetCommand command) {
        if (command.email() == null || command.email().isBlank()) {
            return;
        }
        // Every request counts, a registered e-mail or not, so the limit never tells which e-mails exist; past it
        // nothing is sent, and the answer is the same as ever. Each request sends an e-mail, so without this anyone
        // could fill the mailbox of a reader. The key is a hash: the e-mail itself is not kept, nor logged.
        var attempt = rateLimiter.tryAcquire(rateLimits.passwordResetPolicy(), emailKey(command.email()));
        if (!attempt.allowed()) {
            log.info("A password reset request was over the limit; nothing was sent.");
            return;
        }
        var now = Instant.now(clock);
        for (var reader : readerRepository.findActiveByEmail(command.email())) {
            if (userRepository.findActiveByReaderIdForUpdate(reader.getId()).isEmpty()) {
                continue;
            }
            var token = issue(Purpose.PASSWORD_RESET, reader.getId(), properties.passwordResetValidity(), now);
            notifications.send(new Notification(reader.getEmail(), "Redefinição de senha da biblioteca",
                    "Olá, " + reader.getName() + "!\n\nRecebemos um pedido para redefinir a sua senha. Para escolher uma nova, "
                            + "abra o link abaixo. Ele vale até " + token.expiresAt() + " e só pode ser usado uma "
                            + "vez:\n\n" + properties.passwordResetLink(token.rawToken()) + "\n\nSe não foi você, "
                            + "ignore esta mensagem: a sua senha continua a mesma."));
        }
    }

    @Override
    public void reset(ResetReaderPasswordCommand command) {
        var now = Instant.now(clock);
        var token = usableToken(command.token(), Purpose.PASSWORD_RESET, now, INVALID_RESET_LINK);
        var user = userRepository.findActiveByReaderIdForUpdate(token.getReaderId())
                .orElseThrow(() -> new BusinessRuleException(INVALID_RESET_LINK));
        PasswordPolicy.validate(command.password());
        user.changePasswordHash(passwordHasher.hash(command.password()));
        // the link proves the reader reads the e-mail, so a login lockout from guessed passwords ends here too
        user.unblock();
        userRepository.save(user);
        token.use(now);
        accessTokens.save(token);
        refreshTokens.revokeAllOf(user.getId());
        audit.recordAs(user.getId(), new AuditEvent(AuditAction.READER_PASSWORD_RESET, AuditTarget.USER,
                user.getId(), null));
    }

    @Override
    public void revoke(Long readerId) {
        loadReader(readerId);
        var now = Instant.now(clock);
        accessTokens.invalidatePending(readerId, Purpose.INVITATION, now);
        accessTokens.invalidatePending(readerId, Purpose.PASSWORD_RESET, now);
        Optional<User> access = userRepository.findActiveByReaderIdForUpdate(readerId);
        if (access.isEmpty()) {
            return;
        }
        var user = access.get();
        user.deactivate();
        userRepository.save(user);
        refreshTokens.revokeAllOf(user.getId());
        audit.record(new AuditEvent(AuditAction.READER_ACCESS_REVOKED, AuditTarget.USER, user.getId(),
                "leitor: " + readerId));
    }

    /** The same e-mail whatever the case and the spaces, as a hash. */
    private static String emailKey(String email) {
        return "email:" + ReaderAccessToken.hashOf(email.strip().toLowerCase(Locale.ROOT));
    }

    /** A new link replaces the pending ones of the same kind; only the hash of the new token is kept. */
    private IssuedToken issue(Purpose purpose, Long readerId, java.time.Duration validity, Instant now) {
        accessTokens.invalidatePending(readerId, purpose, now);
        var raw = tokenGenerator.generate();
        var saved = accessTokens.save(ReaderAccessToken.issue(purpose, readerId, ReaderAccessToken.hashOf(raw), now,
                validity));
        return new IssuedToken(raw, saved.getExpiresAt());
    }

    /** The token of a link, locked, when it exists, is of the purpose, is unused and has not expired. */
    private ReaderAccessToken usableToken(String rawToken, Purpose purpose, Instant now, String refusal) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessRuleException(refusal);
        }
        return accessTokens.findByTokenHashForUpdate(ReaderAccessToken.hashOf(rawToken))
                .filter(token -> token.getPurpose() == purpose && token.canBeUsed(now))
                .orElseThrow(() -> new BusinessRuleException(refusal));
    }

    private Reader loadReader(Long id) {
        return readerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("leitor não encontrado: " + id));
    }

    private record IssuedToken(String rawToken, Instant expiresAt) {
    }
}
