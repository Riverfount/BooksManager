package com.riverfount.booksmanager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The PostgreSQL of the tests. There is one container for the whole test run, shared by every Spring context
 * (a container per context made the build slow): it starts with the first context that asks for it, and
 * Testcontainers' Ryuk removes it when the JVM ends. The tests that commit data clean it up themselves, and the
 * ones that need a schema of their own create it inside this database. The version is the one of
 * {@code compose.yaml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

}
