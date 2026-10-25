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
 * {@code compose.yaml}. It accepts 300 connections (the default is 100) because every Spring context of the run keeps
 * its own pool open to it; the pools of the tests are small (see config/application.properties), and this is the margin.
 * fsync is off because the data is thrown away, which is what the image of Testcontainers does by default too.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
            .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=300");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

}
