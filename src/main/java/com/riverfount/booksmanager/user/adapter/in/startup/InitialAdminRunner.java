package com.riverfount.booksmanager.user.adapter.in.startup;

import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, asks for the first administrator to be created from the
 * environment (RF08, RNF10). Restarting is harmless: the use case does
 * nothing once there is any user. Only fixed messages are logged, never the
 * login, the password or the hash.
 */
@Component
@Order(1)
@EnableConfigurationProperties(AdminProperties.class)
class InitialAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminRunner.class);

    private final CreateInitialAdminUseCase createInitialAdminUseCase;
    private final AdminProperties properties;

    InitialAdminRunner(CreateInitialAdminUseCase createInitialAdminUseCase, AdminProperties properties) {
        this.createInitialAdminUseCase = createInitialAdminUseCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = createInitialAdminUseCase.createInitialAdmin(
                new CreateInitialAdminCommand(properties.login(), properties.password()));
        switch (result) {
            case CREATED -> log.info("Initial administrator created from APP_ADMIN_LOGIN and APP_ADMIN_PASSWORD.");
            case NOT_CONFIGURED -> log.warn("There is no user and APP_ADMIN_LOGIN and APP_ADMIN_PASSWORD are not "
                    + "both set, so nobody will be able to log in. Set both and restart.");
            case ALREADY_HAS_USERS -> {
                // nothing to do: the initial administrator is only for an empty system
            }
        }
    }
}
