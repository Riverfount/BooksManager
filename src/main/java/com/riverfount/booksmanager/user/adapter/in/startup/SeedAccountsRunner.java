package com.riverfount.booksmanager.user.adapter.in.startup;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.SeedAccountsUseCase;
import com.riverfount.booksmanager.user.application.port.in.SeedOperatorCommand;
import com.riverfount.booksmanager.user.application.port.in.SeedReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.SeedResult;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, creates the optional operator and reader from the environment, after the initial administrator.
 * Restarting is harmless: a login that already exists is left alone. Only fixed messages are logged, never a
 * login, a password or a hash.
 */
@Component
@Order(2)
@EnableConfigurationProperties(SeedAccountsProperties.class)
class SeedAccountsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAccountsRunner.class);

    private final SeedAccountsUseCase seedAccountsUseCase;
    private final SeedAccountsProperties properties;

    SeedAccountsRunner(SeedAccountsUseCase seedAccountsUseCase, SeedAccountsProperties properties) {
        this.seedAccountsUseCase = seedAccountsUseCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var operator = properties.operator();
        var operatorResult = seedAccountsUseCase.seedOperator(
                new SeedOperatorCommand(operator.login(), operator.password()));
        if (operatorResult == SeedResult.CREATED) {
            log.info("Operator created from APP_OPERATOR_LOGIN and APP_OPERATOR_PASSWORD.");
        } else if (operatorResult == SeedResult.NOT_CONFIGURED && !isBlank(operator.login())) {
            log.warn("The operator was not created: APP_OPERATOR_LOGIN and APP_OPERATOR_PASSWORD must both be set.");
        }

        var reader = properties.reader();
        var readerResult = seedAccountsUseCase.seedReader(new SeedReaderCommand(reader.login(), reader.password(),
                reader.name(), reader.document(), reader.email(), parse(reader.birthDate())));
        if (readerResult == SeedResult.CREATED) {
            log.info("Reader created from the APP_READER_* variables.");
        } else if (readerResult == SeedResult.NOT_CONFIGURED && !isBlank(reader.login())) {
            log.warn("The reader was not created: APP_READER_LOGIN, APP_READER_PASSWORD, APP_READER_NAME, "
                    + "APP_READER_DOCUMENT, APP_READER_EMAIL and APP_READER_BIRTH_DATE must all be set.");
        }
    }

    private static LocalDate parse(String birthDate) {
        if (isBlank(birthDate)) {
            return null;
        }
        try {
            return LocalDate.parse(birthDate.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("APP_READER_BIRTH_DATE deve estar no formato AAAA-MM-DD");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
