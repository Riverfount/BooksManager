package com.riverfount.booksmanager.user.adapter.in.startup;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, recovers the access of the administrator who forgot the password, when APP_ADMIN_RESET_PASSWORD is set
 * together with APP_ADMIN_LOGIN (RF08, RF09). It is for when no other administrator can reset the password in the Users
 * screen. With the variable empty, which is the default, it does nothing and logs nothing.
 *
 * <p>Once the variable is set, every startup warns to remove it, because a password left in the environment is a
 * standing credential. No outcome stops the startup: this runs on a system that already works, unlike the initial
 * administrator, so a wrong login or a weak password only logs a fixed message that says what is wrong. Only fixed
 * messages are logged (and the policy's own message about what the password lacks), never the login, the password or
 * the hash.
 */
@Component
@Order(3)
class AdminPasswordResetRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordResetRunner.class);

    private final ResetAdminPasswordUseCase resetAdminPasswordUseCase;
    private final AdminProperties properties;

    AdminPasswordResetRunner(ResetAdminPasswordUseCase resetAdminPasswordUseCase, AdminProperties properties) {
        this.resetAdminPasswordUseCase = resetAdminPasswordUseCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var password = properties.resetPassword();
        if (password == null || password.isBlank()) {
            return;
        }
        log.warn("APP_ADMIN_RESET_PASSWORD is set. Remove APP_ADMIN_RESET_PASSWORD from the environment as soon as "
                + "the administrator has logged in.");
        try {
            var result = resetAdminPasswordUseCase.reset(new ResetAdminPasswordCommand(properties.login(), password));
            switch (result) {
                case RESET -> log.info("The password of the administrator was reset from APP_ADMIN_RESET_PASSWORD, "
                        + "the login block was cleared and the sessions were closed. Log in with the new password.");
                case ALREADY_UP_TO_DATE -> log.info("The administrator already has this password; nothing changed.");
                case NOT_CONFIGURED -> log.error("APP_ADMIN_LOGIN is not set, so no administrator was recovered. Set "
                        + "it together with APP_ADMIN_RESET_PASSWORD.");
                case NOT_FOUND -> log.error("There is no user with the login in APP_ADMIN_LOGIN, so nothing was "
                        + "reset.");
                case NOT_AN_ADMINISTRATOR -> log.error("The user in APP_ADMIN_LOGIN is not an administrator, so "
                        + "nothing was reset: only an administrator is recovered this way.");
                case INACTIVE -> log.error("The administrator in APP_ADMIN_LOGIN is inactive and stays inactive (the "
                        + "application never reactivates a user), so nothing was reset.");
            }
        } catch (BusinessRuleException e) {
            // the policy's message names what the password lacks, never the password itself
            log.error("APP_ADMIN_RESET_PASSWORD was not applied: {}", e.getMessage());
        }
    }
}
